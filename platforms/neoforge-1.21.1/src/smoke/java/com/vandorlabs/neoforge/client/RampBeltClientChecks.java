package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ramp.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Real connection acceptance of planned horizontal/vertical belt moves and refusal. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class RampBeltClientChecks {
    private static int stage,ticks,received,delay,checks;private static boolean waiting,done;
    private static void need(boolean v,String why){checks++;if(!v)throw new AssertionError("Belt client stage="+stage+": "+why);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Binding {
        @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){if(RampBeltClientFixture.enabled())event.enqueueWork(()->RampBeltClientFixture.client(p->{need(NativeRampRiderClient.receive(p),"real belt S2C accepted sequence="+p.sequence());received++;},RampBeltClientChecks::reply));}
    }
    private static void reply(RampBeltClientFixture.Reply p){var mc=Minecraft.getInstance();need(p.step()==stage&&waiting,"current stage reply");need(p.corrections()==received,"exactly one real correction per admitted player and none for refusal");need(mc.player!=null&&Math.abs(mc.player.getX()-p.x())<1e-7&&Math.abs(mc.player.getY()-p.y())<1e-7&&Math.abs(mc.player.getZ()-p.z())<1e-7,"client physical endpoint matches committed server actors");waiting=false;delay=0;stage++;if(stage==8){need(received==6,"six admitted player steps/resets");done=true;mc.level.disconnect();mc.disconnect();}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!RampBeltClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(done&&mc.level==null){System.out.println("[vandorlabs][ramp-belt-client] client PASS checks="+checks+" corrections="+received);mc.stop();return;}if(!done&&mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)throw new AssertionError("Belt server disconnected stage="+stage);if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1000,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position());if(waiting)return;if(stage==0&&(ticks<100||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().isEmpty()||Math.abs(mc.player.getY()-95)>.01))return;if(++delay<5)return;if(stage==0)System.out.println("[vandorlabs][ramp-belt-client] actual client PID="+ProcessHandle.current().pid());waiting=true;PacketDistributor.sendToServer(new RampBeltClientFixture.Action(stage));}
}
