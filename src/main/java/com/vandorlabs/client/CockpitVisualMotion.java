package com.vandorlabs.client;

import com.vandorlabs.animation.ReversibleVisualPose;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.cockpit.CockpitActuator;
import com.vandorlabs.tiles.TileEntityCockpit;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Cosmetic motion uses the same pause-aware clock as doors, never server world-time corrections. */
public final class CockpitVisualMotion {
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<World,LinkedHashMap<BlockPos,Pose>> WORLDS=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> AUTHORED=new ThreadLocal<>();
    private static final class Pose {
        final ReversibleVisualPose screens=new ReversibleVisualPose(CockpitActuator.DURATION);
        final ReversibleVisualPose seat=new ReversibleVisualPose(CockpitActuator.DURATION);
    }
    private CockpitVisualMotion(){}
    private static double time(){return CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused());}
    static long animationTick(net.minecraft.tileentity.TileEntity tile){return Boolean.TRUE.equals(AUTHORED.get())?tile.getWorld().getTotalWorldTime():(long)time();}
    public static double sample(TileEntityCockpit tile,boolean screens,float partial){
        if(Boolean.TRUE.equals(AUTHORED.get()))return (screens?tile.screenLift:tile.seatSlide).sample(tile.getWorld().getTotalWorldTime()+partial);
        double now=time();
        // A negative partial is the previous-tick rider position, not a clock update.
        return sampleAt(tile,screens,now,partial<0?partial:0);
    }
    static double sampleAt(TileEntityCockpit tile,boolean screens,double now,double offset){
        LinkedHashMap<BlockPos,Pose> poses=WORLDS.computeIfAbsent(tile.getWorld(),world->new LinkedHashMap<>(16,.75F,true));
        // VehicleWorld replaces tiles when settings arrive; retain motion by world/position.
        Pose pose=poses.computeIfAbsent(tile.getPos().toImmutable(),pos->new Pose());
        if(poses.size()>1024)poses.remove(poses.keySet().iterator().next());
        return (screens?pose.screens:pose.seat).sample((screens?tile.screenLift:tile.seatSlide).target(),now,offset);
    }
    /** Deterministic documentation frames explicitly author the saved server timeline. */
    static void authoredFrame(Runnable render){
        Boolean previous=AUTHORED.get();AUTHORED.set(true);
        try{render.run();}finally{if(previous==null)AUTHORED.remove();else AUTHORED.set(previous);}
    }
}
