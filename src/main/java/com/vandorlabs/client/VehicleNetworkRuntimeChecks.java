package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.MathHelper;
import java.io.File;
import java.util.*;

/** Actual remote driver/observer clients, with opt-in server delay/jitter/message-loss injection. */
public final class VehicleNetworkRuntimeChecks {
    private static int phase=-1,elapsed,ticks,samples,reversals;
    private static long lastState=Long.MIN_VALUE,lastSample;
    private static float previousYaw;
    private static boolean sampled,done;
    private static boolean sawOpen,sawClosed;
    private static final Set<net.minecraft.util.math.BlockPos> observedSeats=new HashSet<>();
    private static int seatedSamples;
    private static final Set<Integer> phases=new HashSet<>();
    public static void state(VehicleNetwork.Packet packet){phase=packet.total;elapsed=packet.offset;lastState=System.nanoTime();}
    public static boolean tick(Minecraft mc,File output) throws Exception {
        if(done)return true;
        if(mc.world==null || mc.player==null){if(++ticks>12000)throw new IllegalStateException("dedicated client never connected");return false;}
        mc.displayGuiScreen(null);mc.gameSettings.renderDistanceChunks=4;
        EntityGroundVehicle craft=null;for(EntityGroundVehicle e:VehicleLookup.loaded(mc.world)){craft=e;break;}
        if(craft==null || phase<0)return false;
        VehicleStructure.Cell door=craft.structure.at(new net.minecraft.util.math.BlockPos(0,1,2));
        if(door!=null){if(door.state.getValue(net.minecraft.block.BlockDoor.OPEN))sawOpen=true;else sawClosed=true;}
        if(phase<4)phases.add(phase);
        if(mc.player.getRidingEntity()==craft&&craft.getPassengers().size()==2){
            observedSeats.add(craft.seatForPassenger(mc.player));seatedSamples++;
            if(craft.getControllingPassenger()==null || !"VDriver".equals(craft.getControllingPassenger().getName()))throw new IllegalStateException("Remote passenger took control from the pilot");
        }

        boolean driver=System.getProperty("vandorlabs.networkRole").equals("driver");
        if(driver) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),phase<4);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),phase<4);
        }
        long now=System.nanoTime();
        // A render/JIT pause can span many simulation steps; measure per-frame snaps only during consecutive frames.
        if(sampled && now-lastSample<250000000L && elapsed>40 && elapsed<220 && phase<4) {
            float delta=MathHelper.wrapDegrees(craft.visualPose(1).yaw-previousYaw);
            if(delta<-.1)reversals++;
            if(Math.abs(delta)>15)throw new IllegalStateException("periodic heading snap: "+delta);
            samples++;
        }
        sampled=true;lastSample=now;previousYaw=craft.visualPose(1).yaw;
        if(phase<4)return false;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),false);
        if(phases.size()!=4 || samples<100 || System.nanoTime()-lastState>3000000000L)throw new IllegalStateException("missing latency profiles or stalled observer");
        if(driver && reversals>3)throw new IllegalStateException("correction-induced heading reversals: "+reversals);
        if(seatedSamples<100 || !driver&&observedSeats.size()<craft.structure.passengerSeats().size()-1)throw new IllegalStateException("Remote passenger assignments missing: "+observedSeats.size()+" samples="+seatedSamples);
        if(!sawOpen || !sawClosed)throw new IllegalStateException("remote door state patches missing");
        javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_vehicle_network.png"));
        System.out.println("[vandorlabs][reprolab] vehicle-network PASS role="+System.getProperty("vandorlabs.networkRole")+" profiles=0/50/100/200ms jitter=20ms dropped-input/state samples="+samples+" reversals="+reversals);
        System.out.println("[vandorlabs][reprolab] vehicle-controller "+craft.diagnostics.summary());
        done=true;return true;
    }
}
