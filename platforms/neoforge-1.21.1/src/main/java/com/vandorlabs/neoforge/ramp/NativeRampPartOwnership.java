package com.vandorlabs.neoforge.ramp;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
/** Main-thread local marker classification. Never instantiates a pending tile or serializes a live owner. */
public final class NativeRampPartOwnership {
 public enum State { OWNED,OTHER,COLD,UNKNOWN }
 static final class Trace {long chunks,states,live,pending;void clear(){chunks=states=live=pending=0;}}
 static Trace trace;
 public static State read(ServerLevel level,BlockPos pos,Block block,UUID operation,BlockPos root){
  if(!level.getServer().isSameThread()||operation==null||level.isOutsideBuildHeight(pos))return State.UNKNOWN;var probe=trace;if(probe!=null)probe.chunks++;
  var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)return State.COLD;if(probe!=null)probe.states++;if(!chunk.getBlockState(pos).is(block))return State.OTHER;
  if(probe!=null)probe.live++;var tile=chunk.getBlockEntities().get(pos);if(tile instanceof NativeRampJournalPart part)return part.ownershipKnown()?(part.belongs(operation,root)?State.OWNED:State.OTHER):State.UNKNOWN;if(tile!=null)return State.UNKNOWN;
  if(probe!=null)probe.pending++;var tag=chunk.getBlockEntityNbt(pos);if(tag==null||!tag.contains("NativeRampPartSchema",Tag.TAG_INT)||tag.getInt("NativeRampPartSchema")!=1||!tag.contains("Operation",Tag.TAG_INT_ARRAY))return State.UNKNOWN;if(!tag.contains("RootX",Tag.TAG_INT)||!tag.contains("RootY",Tag.TAG_INT)||!tag.contains("RootZ",Tag.TAG_INT))return State.UNKNOWN;
  int[] words=tag.getIntArray("Operation");if(words.length!=4)return State.UNKNOWN;long hi=operation.getMostSignificantBits(),lo=operation.getLeastSignificantBits();return words[0]==(int)(hi>>>32)&&words[1]==(int)hi&&words[2]==(int)(lo>>>32)&&words[3]==(int)lo&&tag.getInt("RootX")==root.getX()&&tag.getInt("RootY")==root.getY()&&tag.getInt("RootZ")==root.getZ()?State.OWNED:State.OTHER;
 }
 private NativeRampPartOwnership(){}
}
