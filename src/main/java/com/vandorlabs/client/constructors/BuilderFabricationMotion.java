package com.vandorlabs.client.constructors;

import java.util.*;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import com.vandorlabs.tiles.TileEntityConstructor;

/** Pause-aware cosmetic work phase. Repeated packets cannot restart the head or scan. */
final class BuilderFabricationMotion {
    static final class Pose {UUID identity;double now,work;boolean initialized,working;double headX(){return 1.25*Math.sin(work*.07);}double armZ(){return .75*Math.sin(work*.043);}}
    private static final Map<World,LinkedHashMap<BlockPos,Pose>> WORLDS=new WeakHashMap<>();
    static Pose sample(TileEntityConstructor tile){double now=MachineVisualMotion.now();LinkedHashMap<BlockPos,Pose> values=WORLDS.computeIfAbsent(tile.getWorld(),w->new LinkedHashMap<>(16,.75F,true));Pose pose=values.get(tile.getPos());if(pose==null||!tile.renderIdentity().equals(pose.identity)){pose=new Pose();pose.identity=tile.renderIdentity();values.put(tile.getPos().toImmutable(),pose);if(values.size()>256)values.remove(values.keySet().iterator().next());}if(pose.initialized&&pose.working)pose.work+=Math.max(0,now-pose.now);pose.initialized=true;pose.now=now;pose.working=tile.working();return pose;}
    static void clear(World world){WORLDS.remove(world);}
    static void clear(){WORLDS.clear();}
    private BuilderFabricationMotion(){}
}
