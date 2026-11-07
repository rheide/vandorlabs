package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.NativeAuxiliaryLight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Cached emission for lighting workers; only installed game-thread paths discover Screen authority. */
public final class NativeScreenLight {
 private NativeScreenLight(){}
 public static int read(BlockState state,BlockGetter getter,BlockPos pos){
  ServerLevel level=null;LevelChunk chunk=null;
  if(getter instanceof ServerLevel server&&server.getServer().isSameThread()){level=server;chunk=server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);}
  else if(getter instanceof LevelChunk local&&local.getLevel() instanceof ServerLevel server&&server.getServer().isSameThread()&&server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==local){level=server;chunk=local;}
  if(chunk!=null&&chunk.getBlockState(pos)==state&&chunk.getBlockEntities().get(pos) instanceof NativeScreenEntity owner&&bound(level,owner)){NativeScreenPowerService.queryLight(owner);publish(owner);}
  return NativeAuxiliaryLight.read(getter,pos);
 }
 static boolean bound(Level level,NativeScreenEntity owner){if(level==null||owner.isRemoved()||owner.getLevel()!=level)return false;var pos=owner.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(pos)==owner&&chunk.getBlockState(pos)==owner.getBlockState()&&owner.getBlockState().getBlock() instanceof NativeScreenBlock block&&block.family()==owner.family();}
 /** Callers use their game thread; inert installed snapshots deliberately publish darkness. */
 public static boolean publish(NativeScreenEntity owner){var level=owner.getLevel();if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Screen light publication requires the owning server thread");if(!bound(level,owner))return false;return NativeAuxiliaryLight.publish(level,owner.getBlockPos(),owner.inert()?0:owner.visual().appearance().light);}
 /** Actual block removal, never chunk unloading; the replacement publishes its own later scalar. */
 static void removed(Level level,BlockPos pos){if(!(level instanceof ServerLevel server))return;if(!server.getServer().isSameThread())throw new IllegalStateException("Screen light removal requires the server thread");if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null)NativeAuxiliaryLight.publish(level,pos,0);}
}
