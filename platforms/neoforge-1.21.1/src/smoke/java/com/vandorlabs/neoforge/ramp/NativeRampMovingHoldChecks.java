package com.vandorlabs.neoforge.ramp;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
/** Real applied journal; candidate discovery alone does not establish a durable moving header. */
final class NativeRampMovingHoldChecks {
 private static void need(boolean ok,String message){if(!ok)throw new AssertionError("Ramp moving hold: "+message);}
 static void run(ServerLevel level,NativeRampJournal.Operation op,Block block,BlockPos pos){
  var cold=op.root().west(60);need(level.getChunkSource().getChunkNow(cold.getX()>>4,cold.getZ()>>4)==null,"cold candidate precondition");NativeRampHeldOwners.movingCandidates(level,op,List.of(pos,cold),block);
  need(!NativeRampHeldOwners.blocked(level,pos),"empty future remains usable");need(NativeRampHeldOwners.blocked(level,cold)&&level.getChunkSource().getChunkNow(cold.getX()>>4,cold.getZ()>>4)==null,"cold candidate refuses without forcing");
  var trace=new NativeRampPartOwnership.Trace();NativeRampPartOwnership.trace=trace;need(java.util.concurrent.CompletableFuture.supplyAsync(()->NativeRampHeldOwners.blocked(level,pos)).join()&&trace.chunks==0,"worker fail-closed without world reads");
  try{
   level.setBlock(pos,block.defaultBlockState(),18);var part=(NativeRampJournalPart)level.getBlockEntity(pos);part.install(new UUID(9,17),op.root());need(!NativeRampHeldOwners.blocked(level,pos),"other operation remains independent");part.install(op.id(),op.root());need(NativeRampHeldOwners.blocked(level,pos),"newly reached own part held");need(!level.setBlock(pos,Blocks.AIR.defaultBlockState(),18),"ordinary block replacement refused");
   for(int i=0;i<20000;i++)NativeRampHeldOwners.blocked(level,pos);var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();var bytes=new long[3];for(int b=0;b<3;b++){trace.clear();int saves=NativeRampPartOwnershipChecks.serializations;long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)if(!NativeRampHeldOwners.blocked(level,pos))throw new AssertionError("lost candidate hold");bytes[b]=bean.getThreadAllocatedBytes(thread)-before;need(bytes[b]<=64&&trace.chunks==10000&&trace.states==10000&&NativeRampPartOwnershipChecks.serializations==saves,"warm reads/allocation bound "+bytes[b]);}
   var saved=part.saveWithFullMetadata(level.registryAccess());level.removeBlockEntity(pos);var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);chunk.setBlockEntityNbt(saved);need(NativeRampHeldOwners.blocked(level,pos)&&chunk.getBlockEntities().get(pos)==null,"pending own marker held without factory");var unknown=saved.copy();unknown.putInt("NativeRampPartSchema",99);chunk.setBlockEntityNbt(unknown);need(NativeRampHeldOwners.blocked(level,pos)&&chunk.getBlockEntities().get(pos)==null,"unknown marker remains protected");
   try(var scope=NativeRampHeldOwners.mutate(op)){need(level.setBlock(pos,Blocks.AIR.defaultBlockState(),18),"exact operation bypass");}need(!NativeRampHeldOwners.blocked(level,pos),"vacated candidate released");
   System.out.println("[vandorlabs][ramp-moving-holds] PASS own/other/pending/unknown/cold/worker/scoped replacement warmBytes="+Arrays.toString(bytes)+"; no moving-header acceptance");
  }finally{NativeRampPartOwnership.trace=null;NativeRampHeldOwners.movingCandidates(level,op,List.of(),block);}
 }
}
