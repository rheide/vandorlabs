package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Bounded raw root lookup before source chunks or their actor records are admitted.
 * It instantiates no chunks, tiles, entities or capabilities. State decoding belongs to
 * the owning-thread strict registry adapter after this structural lookup. */
final class NativeRampSavedRoot {
 record Root(CompoundTag state,CompoundTag tile){Root{state=state.copy();tile=tile.copy();}public CompoundTag state(){return state.copy();}public CompoundTag tile(){return tile.copy();}}
 static Root read(CompoundTag chunk,BlockPos root){
  if(chunk.sizeInBytes()>8*1024*1024||!chunk.contains("xPos",Tag.TAG_INT)||!chunk.contains("zPos",Tag.TAG_INT)||chunk.getInt("xPos")!=(root.getX()>>4)||chunk.getInt("zPos")!=(root.getZ()>>4)||!chunk.contains("sections",Tag.TAG_LIST)||!chunk.contains("block_entities",Tag.TAG_LIST))throw new IllegalArgumentException("Ramp saved root chunk identity/schema");
  var sections=chunk.getList("sections",Tag.TAG_COMPOUND);if(sections.size()!=((ListTag)chunk.get("sections")).size()||sections.size()>256)throw new IllegalArgumentException("Ramp saved section count");CompoundTag selected=null;
  for(var entry:sections){var section=(CompoundTag)entry;if(!section.contains("Y",Tag.TAG_BYTE))throw new IllegalArgumentException("Ramp saved section coordinate");if(section.getByte("Y")!=(root.getY()>>4))continue;if(selected!=null)throw new IllegalArgumentException("Duplicate saved root section");selected=section;}
  if(selected==null||!selected.contains("block_states",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Ramp saved root state is absent");var states=selected.getCompound("block_states");if(!states.contains("palette",Tag.TAG_LIST))throw new IllegalArgumentException("Ramp saved palette is absent");var palette=states.getList("palette",Tag.TAG_COMPOUND);if(palette.isEmpty()||palette.size()>4096||palette.size()!=((ListTag)states.get("palette")).size())throw new IllegalArgumentException("Ramp saved palette count/type");int chosen=0;
  if(palette.size()>1){int bits=Math.max(4,32-Integer.numberOfLeadingZeros(palette.size()-1)),perLong=64/bits,index=((root.getY()&15)<<8)|((root.getZ()&15)<<4)|(root.getX()&15);if(!states.contains("data",Tag.TAG_LONG_ARRAY))throw new IllegalArgumentException("Ramp saved palette data is absent");long[] words=states.getLongArray("data");if(words.length!=(4096+perLong-1)/perLong)throw new IllegalArgumentException("Ramp saved palette word count");chosen=(int)((words[index/perLong]>>>((index%perLong)*bits))&((1L<<bits)-1));if(chosen>=palette.size())throw new IllegalArgumentException("Ramp saved root palette index");}
  var tiles=chunk.getList("block_entities",Tag.TAG_COMPOUND);if(tiles.size()!=((ListTag)chunk.get("block_entities")).size()||tiles.size()>65536)throw new IllegalArgumentException("Ramp saved tile count/type");CompoundTag tile=null;
  for(var entry:tiles){var candidate=(CompoundTag)entry;for(String key:new String[]{"x","y","z"})if(!candidate.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Ramp saved tile coordinate schema");if(candidate.getInt("x")!=root.getX()||candidate.getInt("y")!=root.getY()||candidate.getInt("z")!=root.getZ())continue;if(tile!=null)throw new IllegalArgumentException("Duplicate saved Ramp root tile");tile=candidate;}
  if(tile==null)throw new IllegalArgumentException("Ramp saved root tile is absent");return new Root(palette.getCompound(chosen),tile);
 }
 private NativeRampSavedRoot(){}
}
