package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ramp.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;

/** Ordinary post-restart lever/menu continuation; no server setup or player mutation. */
public final class NativeRampColdPlayerChecks {
 private static int stage,ticks,wait;private static void need(boolean yes,String message){if(!yes)throw new AssertionError("Ramp cold client "+message+" stage="+stage+" ticks="+ticks);}
 private static int ack(Minecraft mc){var state=mc.level.getBlockState(RampControllerPlayerFixture.ACK);return state.is(Blocks.NOTE_BLOCK)?state.getValue(NoteBlock.NOTE):-1;}
 public static void tick(Minecraft mc){if(stage==4)return;if(stage==3){if(mc.level==null){System.out.println("[vandorlabs][ramp-player-cold] client PASS actual login/lever/reverse/menu/native ownership");stage=4;mc.stop();}return;}need(!(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen),"unexpected login disconnect");if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<2400,"timeout ack="+ack(mc)+" player="+mc.player.position()+" mode="+mc.gameMode.getPlayerMode()+" screen="+mc.screen);if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||!mc.player.getMainHandItem().isEmpty()||mc.player.getInventory().selected!=0||ticks<80)return;need(mc.player.getInventory().getItem(8).getCount()==37,"same actual resource inventory");if(ack(mc)==12){stage=3;mc.level.disconnect();mc.disconnect();return;}if(stage==0&&ack(mc)==10){need(mc.player.getZ()>=-1.6&&mc.player.getZ()<=-1.0,"bounded ordinary approach to visible lever");if(mc.player.getZ()<-1.38){mc.player.setYRot(0);mc.player.setXRot(0);mc.options.keyUp.setDown(true);return;}mc.options.keyUp.setDown(false);wait=0;stage=10;}else if(stage==10){mc.options.keyUp.setDown(false);if(++wait<12)return;System.out.println("[vandorlabs][ramp-player-cold] ordinary approach player="+mc.player.position()+" eye="+mc.player.getEyePosition());RampControllerPlayerChecks.lever(mc);stage=1;}else if(stage==1&&ack(mc)==11){if(++wait<12||!RampControllerPlayerChecks.stoppedSupport(mc))return;RampControllerPlayerChecks.open(mc);stage=2;}else if(stage==2&&mc.screen instanceof NativeRampScreen screen){RampControllerPlayerChecks.offset(screen,0);screen.onClose();stage=5;}else if(stage==5){/* ordinary C2S/native return is pending */}}
 private NativeRampColdPlayerChecks(){}
}
