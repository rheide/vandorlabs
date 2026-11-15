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
    private static LinkedHashMap<BlockPos,Pose> poses(World world){return WORLDS.computeIfAbsent(world,w->new LinkedHashMap<>(16,.75f,true));}
    private static void retain(World world,BlockPos at,Pose pose){LinkedHashMap<BlockPos,Pose> values=poses(world);values.put(at.toImmutable(),pose);if(values.size()>2048)values.remove(values.keySet().iterator().next());}
    /** Vacated source positions must not remain aliases of the carried logical machine. */
    static Pose takeObserved(World world,BlockPos at){Map<BlockPos,Pose> existing=WORLDS.get(world);return existing==null?null:existing.remove(at);}
    static void releaseObserved(World world,BlockPos at,Pose expected){Map<BlockPos,Pose> existing=WORLDS.get(world);if(existing!=null&&existing.get(at)==expected)existing.remove(at);}
    static void installObserved(World world,BlockPos at,Pose pose){if(pose!=null)retain(world,at,pose);}
    public static void clear(World world){WORLDS.remove(world);CoreArmVisualIdentity.clear(world);}
    static Pose sample(TileEntityConstructor tile,boolean active,double duration){
        Double authored=AUTHORED.get();
        double now=authored==null?CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused()):authored;
        return sampleAt(tile,active,duration,now);
    }
    static Pose sampleAt(TileEntityConstructor tile,boolean active,double duration,double now){
        LinkedHashMap<BlockPos,Pose> poses=poses(tile.getWorld());
        Pose pose=poses.get(tile.getPos());
        if(pose==null){pose=CoreArmVisualIdentity.retained(tile);if(pose==null)pose=new Pose();retain(tile.getWorld(),tile.getPos(),pose);}
        if(!pose.initialized){pose.initialized=true;pose.last=now;pose.progress=active?1:0;pose.phase=now;}
        double step=Math.max(0,now-pose.last)/duration;
        pose.progress=active?Math.min(1,pose.progress+step):Math.max(0,pose.progress-step);
        pose.now=now;pose.last=now;CoreArmVisualIdentity.observed(tile,pose);return pose;
    }
    static double eased(double value){return value*value*(3-2*value);}
    private MachineVisualMotion(){}
}
