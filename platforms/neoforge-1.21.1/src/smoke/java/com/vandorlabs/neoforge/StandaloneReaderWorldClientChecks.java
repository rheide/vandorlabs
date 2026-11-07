package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.neoforge.client.NativeCredentialReaderScreen;
import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class StandaloneReaderWorldClientChecks {
 private static int step,phase,ticks,sent;private static boolean captured;private static String capture;
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Binding {
  @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(StandaloneReaderWorldFixture.enabled())event.registerBlockEntityRenderer(StandaloneReaderGeometryChecks.fixtureType(),NativeCredentialReaderRenderer::new);}
  @SubscribeEvent public static void menus(RegisterMenuScreensEvent event){if(StandaloneReaderWorldFixture.enabled())event.<NativeCredentialReaderMenu,NativeCredentialReaderScreen>register(StandaloneReaderGeometryChecks.MENU,NativeCredentialReaderScreen::new);}
  @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)public static void models(ModelEvent.ModifyBakingResult event){if(StandaloneReaderWorldFixture.enabled())NativeCredentialReaderModels.prepare(event.getModels(),java.util.List.of(StandaloneReaderGeometryChecks.fixture(false),StandaloneReaderGeometryChecks.fixture(true)));}
 }
 private static void need(boolean valid,String why){if(!valid)throw new AssertionError("Reader client step="+step+" phase="+phase+": "+why);}
 private static void ack(){PacketDistributor.sendToServer(new StandaloneReaderWorldFixture.Ack(step));step++;phase=0;sent=ticks;}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!StandaloneReaderWorldFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.gameMode==null)return;if(++ticks%100==0)System.out.println("[vandorlabs][reader-world] waiting step="+step+" phase="+phase+" held="+mc.player.getMainHandItem()+" screen="+mc.screen+" block="+mc.level.getBlockState(StandaloneReaderWorldFixture.POS)+" marker="+mc.level.getBlockState(StandaloneReaderWorldFixture.ACK));need(ticks<3600,"timeout mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem()+" selected="+mc.player.getInventory().selected+" pos="+mc.player.position()+" screen="+mc.screen);var marker=mc.level.getBlockState(StandaloneReaderWorldFixture.ACK);if(!marker.is(Blocks.NOTE_BLOCK)||marker.getValue(NoteBlock.NOTE)!=step)return;if(step==12){System.out.println("[vandorlabs][reader-world] client PASS actual survival placement/insertion/return/sneak/tool/editor both kinds; renderer separate screenshots");mc.stop();return;}
  var expected=StandaloneReaderWorldFixture.held(step);if(phase==0){if(mc.screen!=null||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!net.minecraft.world.item.ItemStack.matches(expected,mc.player.getMainHandItem())||mc.player.position().distanceToSqr(.5,96,-3.5)>.01||ticks<sent+15)return;mc.player.setShiftKeyDown(step%6==3);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,step%6==3?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));var pos=step%6==0?StandaloneReaderWorldFixture.SUPPORT:StandaloneReaderWorldFixture.POS;Vec3 target;if(step%6==0)target=new Vec3(.5,96.5,0);else{var bounds=mc.level.getBlockState(pos).getShape(mc.level,pos).bounds();target=new Vec3(pos.getX()+(bounds.minX+bounds.maxX)/2,pos.getY()+(bounds.minY+bounds.maxY)/2,pos.getZ()+(bounds.minZ+bounds.maxZ)/2);}var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(target,Direction.NORTH,pos,false));System.out.println("[vandorlabs][reader-world] action step="+step+" result="+result+" held="+mc.player.getMainHandItem());phase=1;sent=ticks;return;}
  if(!(mc.level.getBlockEntity(StandaloneReaderWorldFixture.POS) instanceof NativeCredentialReaderEntity tile))return;
  switch(step%6){case 0->{if(mc.player.getMainHandItem().getCount()==1&&tile.getBlockState().getBlock()==StandaloneReaderWorldFixture.block(step))ack();}case 1->{if(tile.visual().contents().occupied()&&mc.player.getMainHandItem().getCount()==1)ack();}case 2->{if(!tile.visual().contents().occupied()&&mc.player.getMainHandItem().getCount()==1)ack();}case 3->{if(mc.screen instanceof NativeCredentialReaderScreen)ack();}case 4->{if(phase==1&&mc.screen instanceof NativeCredentialReaderScreen&&mc.player.containerMenu instanceof NativeCredentialReaderMenu menu){PacketDistributor.sendToServer(new ReaderSettingsPayload(menu.containerId,0,true,127,"17",ControlMountSettings.of(1,2,3)));phase=2;}else if(phase==2&&tile.visual().mount().height==1&&tile.visual().mount().tilt==2&&tile.visual().mount().direction==3){mc.player.closeContainer();phase=3;sent=ticks;}else if(phase==3&&mc.screen==null&&ticks>sent+8)ack();}case 5->{if(!tile.visual().contents().occupied()||mc.player.getMainHandItem().getCount()!=1)return;if(phase==1){mc.options.hideGui=true;capture=step>=6?"reader-chip-mounted-world.png":"reader-card-mounted-world.png";captured=false;phase=2;sent=ticks;}else if(phase==2&&captured){mc.options.hideGui=false;ack();}}}
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!StandaloneReaderWorldFixture.enabled()||capture==null||ticks<sent+12)return;String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{captured=true;});}
}
