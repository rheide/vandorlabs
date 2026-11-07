package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Real client modes/inventory/look/use packets and dedicated Canopy editor transport. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CanopyEditorWorldClientChecks {
 private static int step,phase,ticks,sent,window;private static boolean captured;private static String capture;
 @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)public static final class Binding {
  @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CanopyEditorWorldFixture.enabled())NativeCanopyScreen.register(event,CanopyEditorWorldFixture.menuType);}
 }
 private static void need(boolean yes,String why){if(!yes)throw new AssertionError("Canopy editor client step="+step+" phase="+phase+": "+why);}
 private static void ack(){PacketDistributor.sendToServer(new CanopyEditorWorldFixture.Ack(step));step++;phase=0;sent=ticks;}
 private static void click(NativeCanopyScreen screen,int id,int button){var widget=screen.button(id);need(widget!=null&&widget.active,"available widget "+id);need(screen.mouseClicked(widget.getX()+widget.getWidth()/2D,widget.getY()+10,button),"actual button click "+id);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!CanopyEditorWorldFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.gameMode==null)return;ticks++;if(ticks%100==0)System.out.println("[vandorlabs][canopy-editor-world] waiting step="+step+" phase="+phase+" mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem()+" screen="+mc.screen);need(ticks<3000,"timeout mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem()+" slot="+mc.player.getInventory().selected+" position="+mc.player.position()+" screen="+mc.screen);var marker=mc.level.getBlockState(CanopyEditorWorldFixture.ACK);if(!marker.is(Blocks.NOTE_BLOCK)||marker.getValue(NoteBlock.NOTE)!=step)return;
  if(step==6){mc.options.keyShift.setDown(false);System.out.println("[vandorlabs][canopy-editor-world] client PASS ordinary/tool normal+sneak, live widgets, blocked ack, stale window; private editor only");mc.stop();return;}
  BlockPos root=CanopyEditorWorldFixture.root(step);var owner=mc.level.getBlockEntity(root) instanceof NativeCanopyEntity tile?tile:null;if(owner==null||owner.inert())return;
  if(phase==0){if(mc.screen!=null||mc.gameMode.getPlayerMode()!=CanopyEditorWorldFixture.mode(step)||mc.player.getInventory().selected!=0||!ItemStack.matches(mc.player.getMainHandItem(),CanopyEditorWorldFixture.held(step))||mc.player.position().distanceToSqr(root.getX()+.5,100,root.getZ()-3)>.02||ticks<sent+15)return;boolean sneak=CanopyEditorWorldFixture.sneak(step);mc.options.keyShift.setDown(sneak);mc.player.setShiftKeyDown(sneak);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,sneak?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));sent=ticks;phase=10;return;}
  if(phase==10){if(ticks<sent+5)return;mc.gameRenderer.pick(1);need(mc.hitResult instanceof BlockHitResult,"real block ray="+mc.hitResult);var hit=(BlockHitResult)mc.hitResult;need(mc.level.getBlockEntity(hit.getBlockPos()) instanceof NativeCanopyEntity hitTile&&NativeCanopyLifecycle.owner(hitTile).getBlockPos().equals(root),"looked-at canopy owner="+hit.getBlockPos());var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);System.out.println("[vandorlabs][canopy-editor-world] action step="+step+" result="+result+" target="+hit.getBlockPos());sent=ticks;phase=1;return;}
  if(step==3){if(ticks<sent+20)return;need(mc.screen==null,"Survival no-tool refuses editor");ack();return;}
  if(!(mc.screen instanceof NativeCanopyScreen screen)||!(mc.player.containerMenu instanceof NativeCanopyMenu menu))return;
  if(phase==1){need(menu.tile==owner&&menu.getType()==CanopyEditorWorldFixture.menuType,"same dedicated menu bound to actual owner");window=menu.containerId;if(step==4){var slider=screen.children().stream().filter(w->w instanceof NativeIntegerSlider).map(w->(NativeIntegerSlider)w).findFirst().orElseThrow();need(screen.mouseClicked(slider.getX()+slider.getWidth()-1,slider.getY()+10,0),"actual depth slider");screen.mouseDragged(slider.getX()+slider.getWidth()-1,slider.getY()+10,0,100,0);screen.mouseReleased(0,0,0);}else click(screen,2,0);sent=ticks;phase=2;return;}
  if(phase==2){if(step==4){if(screen.error().isEmpty())return;need(screen.editingSettings().data().depth==16&&owner.settings().data().depth==16,"authoritative blocked reply restores draft and owner");}else if(owner.settings().data().tint!=(step<3?(step+2)%4:2))return;if(step==0||step==4){capture="canopy-editor-player-"+step+".png";captured=false;phase=3;}else{screen.onClose();phase=4;}sent=ticks;return;}
  if(phase==3&&captured){screen.onClose();phase=4;sent=ticks;return;}
  if(phase==4){/* Closing changes mc.screen, handled below on the next tick. */}
 }
 @SubscribeEvent public static void closed(ClientTickEvent.Post event){if(!CanopyEditorWorldFixture.enabled()||phase!=4)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||ticks<sent+5)return;if(step==5){var owner=(NativeCanopyEntity)mc.level.getBlockEntity(CanopyEditorWorldFixture.root(step));var tag=new net.minecraft.nbt.CompoundTag();owner.settings().write(tag);tag.putInt("CanopyShade",17);PacketDistributor.sendToServer(new CanopySettingsPayload(new NativeCanopyEdit(owner.getBlockPos(),window,999,NativeCanopySettings.read(tag))));}ack();}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!CanopyEditorWorldFixture.enabled()||capture==null)return;String name=capture;capture=null;var mc=Minecraft.getInstance();net.minecraft.client.Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->captured=true);}
}
