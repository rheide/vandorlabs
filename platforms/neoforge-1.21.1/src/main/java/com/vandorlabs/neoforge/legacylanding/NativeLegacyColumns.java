package com.vandorlabs.neoforge.legacylanding;
import java.util.*;
import net.minecraft.core.BlockPos;
/** Event-driven lowest loaded Ski controller, shared once per column rather than scanned by each tick/getter. */
final class NativeLegacyColumns {
 static void refresh(NativeLegacyLandingEntity physical){long epoch=NativeLegacyUpdates.epoch((net.minecraft.server.level.ServerLevel)physical.getLevel());if(physical.columnEpoch==epoch&&physical.controller()!=null&&!physical.controller().isRemoved())return;if(!physical.kind().ski){physical.controller(physical);physical.columnEpoch=epoch;return;}var world=physical.getLevel();var visited=new LinkedHashSet<NativeLegacyLandingEntity>();var cursor=physical;visited.add(cursor);for(int n=0;n<255;n++){var below=cursor.getBlockPos().below();if(!NativeLegacyLandingBlock.loaded(world,below)||!world.getBlockState(below).is(physical.getBlockState().getBlock()))break;var next=NativeLegacyLandingBlock.owner(world,below);if(next==null||next.getBlockPos().getY()>=cursor.getBlockPos().getY()||!visited.add(next))break;cursor=next;}var leader=cursor;
  // Include physical mounts above this queried member so they share the same immutable answer.
  cursor=physical;for(int n=visited.size();n<256;n++){var above=cursor.getBlockPos().above();if(!NativeLegacyLandingBlock.loaded(world,above)||!world.getBlockState(above).is(physical.getBlockState().getBlock()))break;var next=NativeLegacyLandingBlock.owner(world,above);if(next==null||next.getBlockPos().getY()<=cursor.getBlockPos().getY()||!visited.add(next))break;cursor=next;}
  for(var member:visited){member.controller(leader);member.columnEpoch=epoch;}
 }
 private NativeLegacyColumns(){}
}
