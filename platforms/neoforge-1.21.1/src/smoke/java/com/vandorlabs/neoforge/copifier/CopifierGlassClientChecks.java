package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.glass.NativeGlassEntity;
import com.vandorlabs.ui.CopifierDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CopifierGlassClientChecks {
 private static int stage,ticks,checks;
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierGlassClientFixture.enabled())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}
 private static void need(boolean yes,String why){checks++;if(!yes)throw new IllegalStateException("Copifier Glass client stage="+stage+": "+why);}
 private static void shift(Minecraft mc,boolean value){mc.player.setShiftKeyDown(value);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,value?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
 private static void use(Minecraft mc,net.minecraft.core.BlockPos pos){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,0,-.5),Direction.NORTH,pos,false));}
 private static void click(NativeCopifierScreen screen,int id){var c=screen.controls().stream().filter(v->v.id==id).findFirst().orElseThrow();need(c.enabled&&screen.mouseClicked(c.x+c.width/2D,c.y+7,0),"actual control="+id);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!CopifierGlassClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s){s.onClose();return;}if(stage==11&&mc.level==null){System.out.println("[vandorlabs][copifier-glass-client] client PASS checks="+checks+" actualCapture/editor/connectedGlass/undo/ownedFluids");stage=12;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1400,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" screen="+mc.screen);if(!(mc.level.getBlockEntity(CopifierGlassClientFixture.TARGET) instanceof NativeGlassEntity first)||!(mc.level.getBlockEntity(CopifierGlassClientFixture.PEER) instanceof NativeGlassEntity second))return;
  switch(stage){case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(NativeCopifierItemChecks.ITEM)||mc.player.position().distanceToSqr(.5,96,-3)>.01)return;shift(mc,true);use(mc,CopifierGlassClientFixture.SOURCE);stage=1;}case 1->{var data=NativeCopifierItem.data(mc.player.getMainHandItem());if(!NativeCopifierClipboard.hasCopy(data)||NativeCopifierClipboard.settings(data).getInt("glass_tint")!=3)return;shift(mc,false);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=2;}case 2->{if(!(mc.screen instanceof NativeCopifierScreen screen)||!screen.loaded())return;click(screen,CopifierDialog.CONNECTED);stage=3;}case 3->{if(!(mc.screen instanceof NativeCopifierScreen screen)||!NativeCopifierOptions.connected(NativeCopifierItem.data(screen.getMenu().editorStack())))return;mc.player.closeContainer();stage=4;}case 4->{if(mc.screen instanceof NativeCopifierScreen||mc.player.containerMenu!=mc.player.inventoryMenu)return;use(mc,CopifierGlassClientFixture.TARGET);stage=5;}case 5->{if(first.settings().glass().tint!=3||second.settings().glass().tint!=3||first.settings().glass().size!=0||second.settings().glass().size!=0)return;PacketDistributor.sendToServer(new CopifierGlassClientFixture.Mode(0));mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=6;}case 6->{if(!(mc.screen instanceof NativeCopifierScreen screen)||!screen.loaded()||!mc.level.getBlockState(CopifierGlassClientFixture.ACK).is(Blocks.STONE))return;click(screen,CopifierDialog.UNDO);stage=7;}case 7->{if(first.settings().glass().tint!=1||second.settings().glass().tint!=1||first.settings().glass().size!=1||second.settings().glass().size!=1)return;PacketDistributor.sendToServer(new CopifierGlassClientFixture.Mode(1));mc.player.closeContainer();stage=8;}case 8->{if(mc.screen instanceof NativeCopifierScreen||!mc.level.getBlockState(CopifierGlassClientFixture.ACK.east()).is(Blocks.STONE))return;PacketDistributor.sendToServer(new CopifierGlassClientFixture.Mode(2));stage=9;}case 9->{if(!mc.level.getBlockState(CopifierGlassClientFixture.ACK.east(2)).is(Blocks.STONE))return;mc.level.disconnect();mc.disconnect();stage=11;}default->{}}
 }
}
