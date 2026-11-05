package com.vandorlabs.neoforge.fluid;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Current bounded sponge search through contained water and same-tick removed native water. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeFluidSponge {
 private NativeFluidSponge(){}
 private static final Map<ServerLevel,Search> LEVELS=Collections.synchronizedMap(new IdentityHashMap<>());
 private static boolean dry(BlockState state){return state.is(Blocks.SPONGE);}
 private static boolean water(BlockState state){var fluid=state.getFluidState().getType();return fluid==Fluids.WATER||fluid==Fluids.FLOWING_WATER;}
 public static void changed(ServerLevel level,BlockPos pos){if(!NativeFluidWorld.open(level))return;var search=LEVELS.computeIfAbsent(level,key->new Search());if(search.absorbing)return;for(Direction face:Direction.values()){BlockPos next=pos.relative(face);if(NativeFluidWorld.loaded(level,next)&&dry(level.getBlockState(next)))search.absorb(level,next,false);}}
 /** Called after an actual installed chunk state replacement, never during snapshot capture. */
 public static void observe(ServerLevel level,BlockPos pos,BlockState before,BlockState after){
  if(!NativeFluidWorld.open(level)||level.captureBlockSnapshots)return;
  if(!water(before)&&!dry(before)&&!dry(after)&&!after.is(Blocks.WET_SPONGE)&&!after.hasBlockEntity()){var existing=LEVELS.get(level);if(existing!=null&&!after.isAir())existing.removed.remove(pos.asLong());return;}
  if(!NativeFluidWorld.loaded(level,pos))return;
  var search=LEVELS.computeIfAbsent(level,key->new Search());if(search.absorbing)return;
  if(search.tick!=level.getGameTime()){search.removed.clear();search.tick=level.getGameTime();}
  if(water(before)&&!water(after)&&(after.isAir()||before.getBlock()==after.getBlock()&&after.getBlock() instanceof net.minecraft.world.level.block.BucketPickup)){if(search.removed.size()<4096)search.removed.add(pos.asLong());}else if(!after.isAir())search.removed.remove(pos.asLong());
  BlockState current=level.getBlockState(pos);if(dry(current)||dry(before)&&current.is(Blocks.WET_SPONGE)){search.absorb(level,pos,!dry(current));return;}
  if(level.getBlockEntity(pos) instanceof ContainedFluidHost)changed(level,pos);
 }
 @SubscribeEvent public static void unload(LevelEvent.Unload event){LEVELS.remove(event.getLevel());}
 private static final class Search {
  final Set<Long> removed=new HashSet<>();long tick=Long.MIN_VALUE;boolean absorbing;
  void absorb(ServerLevel level,BlockPos sponge,boolean vanillaWet){absorbing=true;try{
   ArrayDeque<BlockPos> queue=new ArrayDeque<>();ArrayDeque<Integer> depths=new ArrayDeque<>();Set<BlockPos> visited=new HashSet<>();List<BlockPos> drained=new ArrayList<>();queue.add(sponge);depths.add(0);visited.add(sponge);int count=0;boolean contained=false;
   while(!queue.isEmpty()&&count<64){BlockPos at=queue.remove();int depth=depths.remove();for(Direction face:Direction.values()){
    BlockPos next=at.relative(face);if(!visited.add(next)||!NativeFluidWorld.loaded(level,next))continue;BlockState state=level.getBlockState(next);boolean bridge=vanillaWet&&(state.isAir()||state.getBlock() instanceof net.minecraft.world.level.block.BucketPickup&&!water(state))&&removed.contains(next.asLong()),wet=water(state),stored=false;
    if(state.hasBlockEntity()&&level.getBlockEntity(next) instanceof ContainedFluidHost host)for(int side=0;side<2;side++){var fluid=host.containedFluids().get(side);if(!fluid.isEmpty()&&fluid.is(Fluids.WATER)){stored|=NativeFluidWorld.set(host,side,net.neoforged.neoforge.fluids.FluidStack.EMPTY);}}
    if(!bridge&&!wet&&!stored)continue;if(wet){if(state.getBlock() instanceof net.minecraft.world.level.block.BucketPickup pickup){if(pickup.pickupBlock(null,level,next,state).isEmpty())continue;}else if(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)level.setBlock(next,Blocks.AIR.defaultBlockState(),2);else continue;drained.add(next);}contained|=stored;count++;if(depth<6){queue.add(next);depths.add(depth+1);}if(count>=64)break;
   }}
   if(contained||!drained.isEmpty()){if(dry(level.getBlockState(sponge)))level.setBlock(sponge,Blocks.WET_SPONGE.defaultBlockState(),2);for(BlockPos pos:drained)for(Direction face:Direction.values()){BlockPos neighbor=pos.relative(face);if(NativeFluidWorld.loaded(level,neighbor))level.neighborChanged(neighbor,Blocks.AIR,pos);}if(!vanillaWet)level.levelEvent(2001,sponge,net.minecraft.world.level.block.Block.getId(Blocks.WATER.defaultBlockState()));}
  }finally{absorbing=false;}}
 }
}
