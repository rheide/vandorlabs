package com.vandorlabs.neoforge.storage;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Local onLoad queue; weak world values cannot retain a level through their owners. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeStorageLifecycle {
 private static final Map<ServerLevel,WeakHashMap<NativeStorageEntity,Boolean>> PENDING=new WeakHashMap<>();
 private static final Direction[] SIDES={Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
 private NativeStorageLifecycle(){}
 public static void queue(NativeStorageEntity owner){if(owner.getLevel() instanceof ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("Storage lifecycle off server thread");PENDING.computeIfAbsent(level,key->new WeakHashMap<>()).put(owner,Boolean.TRUE);}}
 public static void retired(NativeStorageEntity owner){if(owner.getLevel() instanceof ServerLevel level){var pending=PENDING.get(level);if(pending!=null)pending.remove(owner);}}
 private static boolean loaded(ServerLevel level,net.minecraft.core.BlockPos pos){return !level.isOutsideBuildHeight(pos)&&level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
 public static void comparators(NativeStorageEntity owner){if(!owner.installed())return;var level=(ServerLevel)owner.getLevel();comparators(level,owner.getBlockPos(),owner.getBlockState().getBlock());}
 public static void comparators(ServerLevel level,net.minecraft.core.BlockPos origin,net.minecraft.world.level.block.Block source){for(Direction side:SIDES){var pos=origin.relative(side);if(!loaded(level,pos))continue;var state=level.getBlockState(pos);if(state.is(Blocks.COMPARATOR))level.neighborChanged(pos,source,origin);else if(state.isRedstoneConductor(level,pos)){pos=pos.relative(side);if(loaded(level,pos)&&level.getBlockState(pos).is(Blocks.COMPARATOR))level.neighborChanged(pos,source,origin);}}}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel level))return;var pending=PENDING.remove(level);if(pending==null)return;for(var owner:pending.keySet())if(owner!=null&&owner.installed())comparators(owner);}
 @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){PENDING.remove(event.getLevel());}
}
