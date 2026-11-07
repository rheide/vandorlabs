package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.ui.CopifierDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
/** One actual interaction/widget/packet journey shared by target-family client regressions. */
final class CopifierCopyClientJourney {
 interface Scenario {
  boolean enabled();String label();BlockPos source();BlockPos target();BlockPos ack();
  boolean targetsReady(Minecraft mc);boolean copied(CompoundTag clipboard);boolean applied(Minecraft mc);boolean undone(Minecraft mc);void confirm(int operation);
 }
 private final Scenario scenario;private int stage,ticks,checks;
 CopifierCopyClientJourney(Scenario scenario){this.scenario=scenario;}
 private void need(boolean yes,String why){checks++;if(!yes)throw new IllegalStateException("Copifier "+scenario.label()+" client stage="+stage+": "+why);}
 private void shift(Minecraft mc,boolean value){mc.player.setShiftKeyDown(value);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,value?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
 private void use(Minecraft mc,net.minecraft.core.BlockPos pos){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,0,-.5),Direction.NORTH,pos,false));}
 private void click(NativeCopifierScreen screen,int id){var c=screen.controls().stream().filter(v->v.id==id).findFirst().orElseThrow();need(c.enabled&&screen.mouseClicked(c.x+c.width/2D,c.y+7,0),"actual control="+id);}
 public void tick(){if(!scenario.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s){s.onClose();return;}if(stage==11&&mc.level==null){System.out.println("[vandorlabs]["+scenario.label()+"] client PASS checks="+checks+" actualCapture/editor/connectedTargets/undo");stage=12;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1400,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" screen="+mc.screen);if(!scenario.targetsReady(mc))return;
  switch(stage){case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(NativeCopifierItemChecks.ITEM)||mc.player.position().distanceToSqr(.5,96,-3)>.01)return;shift(mc,true);use(mc,scenario.source());stage=1;}case 1->{var data=NativeCopifierItem.data(mc.player.getMainHandItem());if(!NativeCopifierClipboard.hasCopy(data)||!scenario.copied(NativeCopifierClipboard.settings(data)))return;shift(mc,false);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=2;}case 2->{if(!(mc.screen instanceof NativeCopifierScreen screen)||!screen.loaded())return;click(screen,CopifierDialog.CONNECTED);stage=3;}case 3->{if(!(mc.screen instanceof NativeCopifierScreen screen)||!NativeCopifierOptions.connected(NativeCopifierItem.data(screen.getMenu().editorStack())))return;mc.player.closeContainer();stage=4;}case 4->{if(mc.screen instanceof NativeCopifierScreen||mc.player.containerMenu!=mc.player.inventoryMenu)return;use(mc,scenario.target());stage=5;}case 5->{if(!scenario.applied(mc))return;scenario.confirm(0);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=6;}case 6->{if(!(mc.screen instanceof NativeCopifierScreen screen)||!screen.loaded()||!mc.level.getBlockState(scenario.ack()).is(Blocks.STONE))return;click(screen,CopifierDialog.UNDO);stage=7;}case 7->{if(!scenario.undone(mc))return;scenario.confirm(1);mc.player.closeContainer();stage=8;}case 8->{if(mc.screen instanceof NativeCopifierScreen||!mc.level.getBlockState(scenario.ack().east()).is(Blocks.STONE))return;scenario.confirm(2);stage=9;}case 9->{if(!mc.level.getBlockState(scenario.ack().east(2)).is(Blocks.STONE))return;mc.level.disconnect();mc.disconnect();stage=11;}default->{}}
 }
}
