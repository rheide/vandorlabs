package com.vandorlabs.client.constructors;

import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.tiles.TileEntityConstructor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Cosmetic motion uses local elapsed ticks and survives repeated tile packets. */
public final class MachineVisualMotion {
    private static final VisualClock CLOCK=new VisualClock();
    private static final ThreadLocal<Double> AUTHORED=new ThreadLocal<>();
    /** Hold unrelated mechanism motion fixed during isolated surface/reader pixel checks. */
    public static void at(double tick,Runnable draw){
        Double previous=AUTHORED.get();AUTHORED.set(tick);
        try{draw.run();}finally{if(previous==null)AUTHORED.remove();else AUTHORED.set(previous);}
    }
    private static final Map<World,LinkedHashMap<BlockPos,Pose>> WORLDS=new WeakHashMap<>();
    static final class Pose {double progress,now,last,phase;boolean initialized;}
    static Pose sample(TileEntityConstructor tile,boolean active,double duration){
        Double authored=AUTHORED.get();
        double now=authored==null?CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused()):authored;
        return sampleAt(tile,active,duration,now);
    }
    static Pose sampleAt(TileEntityConstructor tile,boolean active,double duration,double now){
        LinkedHashMap<BlockPos,Pose> poses=WORLDS.computeIfAbsent(tile.getWorld(),w->new LinkedHashMap<>(16,.75f,true));
        Pose pose=poses.get(tile.getPos());
        if(pose==null){pose=new Pose();poses.put(tile.getPos().toImmutable(),pose);if(poses.size()>2048)poses.remove(poses.keySet().iterator().next());}
        if(!pose.initialized){pose.initialized=true;pose.last=now;pose.progress=active?1:0;pose.phase=now;}
        double step=Math.max(0,now-pose.last)/duration;
        pose.progress=active?Math.min(1,pose.progress+step):Math.max(0,pose.progress-step);
        pose.now=now;pose.last=now;return pose;
    }
    static double eased(double value){return value*value*(3-2*value);}
    private MachineVisualMotion(){}
}
