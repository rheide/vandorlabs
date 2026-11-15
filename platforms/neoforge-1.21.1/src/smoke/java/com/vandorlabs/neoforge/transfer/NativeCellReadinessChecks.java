package com.vandorlabs.neoforge.transfer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
/** Property-gated extension of the existing real cell journal fixture, including actual native entity reads. */
final class NativeCellReadinessChecks {
 private static final BlockPos SOURCE=new BlockPos(15,110,8),TARGET=SOURCE.east();
 private record Key(UUID operation,boolean rollback){}
 private static final Map<Key,Gate> GATES=new HashMap<>();private static final Set<Key> CHECKPOINTED=new HashSet<>();private static final Set<String> ROLES=new HashSet<>();private static final Set<String> DIAGNOSTICS=new HashSet<>();private static int checks,pending,actualPending,probes,sequence;
 private static boolean enabled(){return Boolean.getBoolean("vandorlabs.cellReadinessChecks");}
 private static void need(boolean value,String why){checks++;if(!value){if(DIAGNOSTICS.size()<8&&DIAGNOSTICS.add(why))System.err.println("[vandorlabs][cell-readiness] first refusal: "+why);throw new AssertionError("Cell receipt readiness: "+why);}}
 private static final class Gate {final ServerLevel world;final NativeCellTransactions.Header header;final NativeRawCell source,target;final NativeEntityCheckpoint actors;final String role;int polls;boolean open,unrelatedServerTask;
  Gate(ServerLevel world,NativeCellTransactions.Header header,String role){this.world=world;this.header=header;this.role=role;if(world.getServer().getTickCount()==0)world.getServer().tell(new net.minecraft.server.TickTask(0,()->unrelatedServerTask=true));source=capture(world,SOURCE);target=capture(world,TARGET);var chunk=new ChunkPos(3+(sequence++%4),0);world.getChunk(chunk.x,chunk.z);need(world.getChunkSource().getChunkNow(chunk.x,chunk.z)!=null,"actor block chunk is loaded before readiness");actors=new NativeEntityCheckpoint(world,List.of(chunk),()->need(NativeCellTransactions.reservedBy(world,header,header.root()),"exact actor checkpoint authority"));}
  boolean poll(){if(open)return true;polls++;need(NativeTransferHolds.blocked(world,SOURCE)&&NativeTransferHolds.blocked(world,TARGET)&&NativeCellTransactions.reservedBy(world,header,SOURCE),"pending receipt retains the exact operation lease");need(source.identical(capture(world,SOURCE))&&target.identical(capture(world,TARGET)),"pending actor receipt leaves both exact cell owners untouched");need(!world.setBlock(SOURCE,Blocks.EMERALD_BLOCK.defaultBlockState(),3),"pending receipt never grants a write");boolean loaded=actors.loaded();if(world.getServer().getTickCount()==0)need(!unrelatedServerTask,"startup actor readiness never drains unrelated server tasks");if(!loaded){actualPending++;if(polls==1000)System.err.println("[vandorlabs][cell-readiness] still pending role="+role+" serverTicks="+world.getServer().getTickCount()+" source="+world.getBlockState(SOURCE)+" target="+world.getBlockState(TARGET));}if(polls<4||!loaded){pending++;return false;}open=true;ROLES.add(role);return true;}
 }
 private static NativeRawCell capture(ServerLevel world,BlockPos at){var tile=world.getBlockEntity(at);return new NativeRawCell(at,world.getBlockState(at),tile==null?null:tile.saveWithFullMetadata(world.registryAccess()),256*1024);}
 static boolean ready(ServerLevel world,NativeCellTransactions.Header header,boolean rollback){if(!enabled())return true;var key=new Key(header.operation(),rollback);return GATES.computeIfAbsent(key,unused->new Gate(world,header,rollback?"recovery":"installation")).poll();}
 static void installed(ServerLevel world,NativeCellTransactions.Header header){reset(world,header,false,"post-install");}
 static void rolledBack(ServerLevel world,NativeCellTransactions.Header header){reset(world,header,true,"post-rollback");}
 private static void reset(ServerLevel world,NativeCellTransactions.Header header,boolean rollback,String role){if(enabled())GATES.put(new Key(header.operation(),rollback),new Gate(world,header,role));}
 static boolean matches(NativeCellTransactions.Header header,boolean rollback){if(!enabled())return true;probes++;var gate=GATES.get(new Key(header.operation(),rollback));need(gate!=null&&gate.open,"additionalMatches never probes pending entity sections");return true;}
 static boolean checkpoint(ServerLevel world,NativeCellTransactions.Header header,boolean rollback){if(!enabled())return true;need(matches(header,rollback),"receipt checkpoint starts after actor readiness");var key=new Key(header.operation(),rollback);if(CHECKPOINTED.add(key))reset(world,header,rollback,"committing");return true;}
 static void committed(ServerLevel world,NativeCellTransactions.Header header,boolean rollback){reset(world,header,rollback,"retiring");}
 static void verify(boolean cold){if(!enabled())return;need(pending>0&&actualPending>0&&probes>0,"real native actor-section pending was observed and eventually probed");if(!cold)for(String role:List.of("installation","recovery","post-install","post-rollback","committing","retiring"))need(ROLES.contains(role),"receipt wait covered "+role);System.out.println("[vandorlabs][cell-readiness] PASS checks="+checks+" waits="+pending+" actualEntityPending="+actualPending+" probes="+probes+" cold="+cold+" roles="+ROLES);}
 private NativeCellReadinessChecks(){}
}
