package com.vandorlabs.neoforge.ramp;
import java.util.*;
import net.minecraft.core.BlockPos;
/** Bounded source-to-cell preparation; no cell-by-all-origins Cartesian pass in moving part publication. */
public final class NativeRampSourceIndex {
 private final Map<BlockPos,List<NativeRampGeometry.Origin>> cells;private final long retainedBytes;
 private NativeRampSourceIndex(Map<BlockPos,List<NativeRampGeometry.Origin>> cells,long bytes){this.cells=Collections.unmodifiableMap(cells);retainedBytes=bytes;}
 public List<NativeRampGeometry.Origin> origins(BlockPos cell){return cells.getOrDefault(cell,List.of());}
 public Set<BlockPos> cells(){return cells.keySet();}public long retainedBytes(){return retainedBytes;}
 public static NativeRampSourceIndex prepare(NativeRampFootprint.Spec spec){var mutable=new LinkedHashMap<BlockPos,ArrayList<NativeRampGeometry.Origin>>();long bytes=160,members=0;for(var origin:spec.origins())bytes+=128L+80L*origin.shapes().size();
  for(var origin:spec.origins()){var single=new NativeRampFootprint.Spec(spec.root(),spec.facing(),spec.sourceY(),spec.minimumAlong(),spec.length(),spec.low(),spec.high(),spec.start(),spec.end(),spec.treadPixels(),spec.elevator(),spec.travel(),spec.extend(),spec.fast(),spec.interpolation(),spec.verticalLayout(),spec.moat(),List.of(origin));for(var cell:NativeRampFootprint.cells(single,0,1)){var list=mutable.get(cell);if(list==null){if(mutable.size()>=8192)throw new IllegalArgumentException("Ramp source index cell budget");bytes+=128;list=new ArrayList<>();mutable.put(cell,list);}if(++members>131072||(bytes+=8)>8L*1024*1024)throw new IllegalArgumentException("Ramp source index memory budget");list.add(origin);}}
  var frozen=new LinkedHashMap<BlockPos,List<NativeRampGeometry.Origin>>();for(var entry:mutable.entrySet())frozen.put(entry.getKey(),List.copyOf(entry.getValue()));return new NativeRampSourceIndex(frozen,bytes);
 }
}
