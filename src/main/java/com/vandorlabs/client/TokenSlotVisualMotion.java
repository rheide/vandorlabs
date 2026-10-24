package com.vandorlabs.client;

import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Shared reader motion survives tile packets and ignores client/server clock jumps. */
final class TokenSlotVisualMotion {
    private static final ThreadLocal<Double> AUTHORED=new ThreadLocal<>();
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<World,LinkedHashMap<BlockPos,Pose[]>> WORLDS=new WeakHashMap<>();
    private static final class Pose {boolean present;long started,sequence;double time;}
    private TokenSlotVisualMotion(){}
    static float age(TileEntityTokenSlot tile){Double authored=AUTHORED.get();return ageAt(tile,authored==null?CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused()):authored);}
    static void at(double tick,Runnable draw){Double previous=AUTHORED.get();AUTHORED.set(tick);try{draw.run();}finally{if(previous==null)AUTHORED.remove();else AUTHORED.set(previous);}}
    static float ageAt(TileEntityTokenSlot tile,double now){
        LinkedHashMap<BlockPos,Pose[]> poses=WORLDS.computeIfAbsent(tile.getWorld(),w->new LinkedHashMap<>(16,.75F,true));Pose[] slots=poses.get(tile.getPos());
        if(slots==null){slots=new Pose[3];poses.put(tile.getPos().toImmutable(),slots);if(poses.size()>2048)poses.remove(poses.keySet().iterator().next());}
        Pose pose=slots[tile.visualSlot()];
        if(pose==null){pose=new Pose();pose.present=!tile.isEmpty();pose.started=tile.insertionStarted();pose.sequence=tile.insertionSequence();pose.time=now-(pose.present?tile.animationAge(0):20);slots[tile.visualSlot()]=pose;}
        if(tile.isEmpty()){pose.present=false;return 20;}
        if(!pose.present||pose.started!=tile.insertionStarted()||pose.sequence!=tile.insertionSequence()){pose.present=true;pose.started=tile.insertionStarted();pose.sequence=tile.insertionSequence();pose.time=now;}
        return (float)Math.max(0,Math.min(20,now-pose.time));
    }
}
