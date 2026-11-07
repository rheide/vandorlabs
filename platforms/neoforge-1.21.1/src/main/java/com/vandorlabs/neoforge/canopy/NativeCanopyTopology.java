package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.canopy.CanopyConnections;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Loaded contact publication. Structural events coalesce by section; no renderer discovers neighbors. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCanopyTopology {
    public record Masks(int mask,int roof){}
    private static final Direction[] HORIZONTAL={Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST};
    private static final class Dirty {final Set<BlockPos> sections=new HashSet<>();boolean all;}
    private static final Map<Level,Dirty> PENDING=new WeakHashMap<>();
    private NativeCanopyTopology(){}
    private static boolean opening(NativeCanopyEntity host){return host.kind().equals("canopy")||host.kind().equals("long_canopy");}
    private static int length(NativeCanopyEntity host){return NativeCanopyModels.builtin().dimensions(host.kind()).length();}
    /** Released logical rear cells remain discoverable after the physical helper yields to a mechanism. */
    static NativeCanopyEntity logicalAt(Level level,BlockPos pos){var direct=NativeCanopyLifecycle.at(level,pos);if(direct!=null)return direct;for(var direction:HORIZONTAL)for(int distance=1;distance<=2;distance++){var candidate=NativeCanopyLifecycle.at(level,pos.relative(direction,distance));if(candidate==null||!candidate.available()||!opening(candidate)||NativeCanopyLifecycle.constituent(candidate)!=candidate)continue;var cells=NativeCanopyFootprint.cells(candidate.kind(),candidate.getBlockPos(),candidate.facing(),candidate.settings(),candidate.links());if(candidate.links().released().contains(cells.indexOf(pos)))return candidate;}return null;}
    public static Masks resolve(NativeCanopyEntity host){var level=host.getLevel();if(level==null||!host.available()||!NativeCanopyLifecycle.installed(host))return new Masks(0,0);int mask=0,roof=0;var pos=host.getBlockPos();var facing=host.facing();
        for(int bit=1;bit<=8;bit<<=1){var direction=bit==1?facing.getCounterClockWise():bit==2?facing.getClockWise():bit==4?facing:facing.getOpposite();var peer=logicalAt(level,pos.relative(direction,bit==8?length(host):1));if(peer!=null&&host.kind().equals("regular_canopy_glass"))peer=NativeCanopyLifecycle.constituent(peer);if(peer!=null&&opening(host)&&bit==8&&peer.facing()==facing.getOpposite())peer=NativeCanopyLifecycle.constituent(peer);if(peer==null||!peer.available())continue;var other=peer.getBlockPos();if(!CanopyConnections.connects(bit,host.kind(),length(host),facing.get3DDataValue(),host.settings().data().mount,peer.kind(),length(peer),peer.facing().get3DDataValue(),peer.settings().data().mount,other.getX()-pos.getX(),other.getY()-pos.getY(),other.getZ()-pos.getZ(),NativeCanopyLifecycle.constituent(peer)==peer))continue;mask|=bit;var above=logicalAt(level,pos.relative(direction));if(above==null||!above.available()||!CanopyConnections.cutsRoof(NativeCanopyLifecycle.constituent(above).kind()))roof|=bit;
        }return new Masks(mask,roof);
    }
    static synchronized void queue(Level level,BlockPos pos){if(level==null||!NativeCanopyTargets.hasMembers(level))return;var section=new BlockPos(pos.getX()>>4<<4,pos.getY()>>4<<4,pos.getZ()>>4<<4);var dirty=PENDING.get(level);if(dirty!=null&&(dirty.all||dirty.sections.contains(section)))return;if(!NativeCanopyTargets.nearby(level,new AABB(section.getX(),section.getY(),section.getZ(),section.getX()+16,section.getY()+16,section.getZ()+16)))return;if(dirty==null){dirty=new Dirty();PENDING.put(level,dirty);}if(dirty.sections.size()>=8192){dirty.sections.clear();dirty.all=true;return;}dirty.sections.add(section);}
    @SubscribeEvent public static void neighbors(BlockEvent.NeighborNotifyEvent event){if(event.getLevel() instanceof Level level)queue(level,event.getPos());}
    @SubscribeEvent public static void loaded(ChunkEvent.Load event){if(event.getLevel() instanceof Level level){var pos=event.getChunk().getPos();for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y+=16)queue(level,new BlockPos(pos.getMinBlockX(),y,pos.getMinBlockZ()));}}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){var level=event.getLevel();Dirty dirty;synchronized(NativeCanopyTopology.class){dirty=PENDING.remove(level);}if(dirty==null)return;java.util.function.Consumer<NativeCanopyEntity> mark=host->{if(host.available()&&host.publication().draws())host.topologyDirty();};if(dirty.all){NativeCanopyTargets.visit(level,new AABB(-30_000_000,level.getMinBuildHeight(),-30_000_000,30_000_000,level.getMaxBuildHeight(),30_000_000),mark);return;}for(var section:dirty.sections)NativeCanopyTargets.visit(level,new AABB(section.getX()-8,section.getY()-8,section.getZ()-8,section.getX()+24,section.getY()+24,section.getZ()+24),mark);}
    @SubscribeEvent public static synchronized void unload(LevelEvent.Unload event){if(event.getLevel() instanceof Level level)PENDING.remove(level);}
}
