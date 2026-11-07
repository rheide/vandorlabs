package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Immutable generation recipe built from already-staged source snapshots. Not mutation authority. */
public final class NativeRampAssembly {
 private static final int[] NONE={-1,-1,-1,-1,-1,-1};
 private final NativeRampFootprint.Spec footprint;private final NativeRampSettings settings;private final BlockState original;
 private final NativeRampSourceIndex index;private final Map<BlockPos,NativeRampPartData.Origin> sources;private final Map<Integer,List<NativeRampPartData.Origin>> columns;
 private final Set<BlockPos> potential,beltCells;private final long retainedBytes;
 private NativeRampAssembly(BlockPos root,NativeRampSettings settings,NativeRampCapture.Plan plan,Map<BlockPos,CompoundTag> snapshots){
  this.settings=Objects.requireNonNull(settings);footprint=NativeRampClearance.footprint(root,settings,plan);original=NativeRampPartData.storedState(plan.sources().getFirst().state());
  var origins=new LinkedHashMap<BlockPos,NativeRampPartData.Origin>();var columnLists=new HashMap<Integer,ArrayList<NativeRampPartData.Origin>>();long bytes=512;
  for(var source:plan.sources()){
   var state=source.state();var tag=snapshots.get(source.position());boolean housing=state.getBlock().getClass()==ProgrammableBlock.class||state.getBlock() instanceof NativeProgrammableSlab||state.getBlock() instanceof NativeProgrammableStairs;boolean stairs=state.getBlock() instanceof NativeProgrammableStairs;int packed=8;
   var material=new NativeRampPartMaterial(state,-1,-1,-1,0,false,false,NONE);
   if(housing){if(tag==null)throw new IllegalArgumentException("Missing staged Ramp housing");var fields=new ModernHousingTags(tag);var saved=HousingSettings.read(fields,0,true,ModernFinishCatalog::storedChoice,ModernFinishCatalog::validChoice);var facing=stairs?state.getValue(StairBlock.FACING):state.getValue(BlockStateProperties.FACING);int rotation=stairs?facing.get2DDataValue():(2-(facing.get2DDataValue()&3))&3;material=new NativeRampPartMaterial(state,saved.mainTexture,stairs?saved.sideTexture:-1,stairs?saved.layout():saved.rawLayout,rotation,stairs,saved.facesEnabled,saved.faces());if(stairs)packed=StairState.read(fields).packed();}
   var origin=new NativeRampPartData.Origin(source.position(),packed,material);if(!origin.geometry().shapes().equals(source.shapes()))throw new IllegalArgumentException("Staged Ramp shape changed");if(origins.put(source.position(),origin)!=null)throw new IllegalArgumentException("Duplicate Ramp source");columnLists.computeIfAbsent(column(source.position()),unused->new ArrayList<>()).add(origin);bytes+=320+80L*source.shapes().size();
  }
  sources=Collections.unmodifiableMap(origins);var frozen=new HashMap<Integer,List<NativeRampPartData.Origin>>();for(var entry:columnLists.entrySet())frozen.put(entry.getKey(),List.copyOf(entry.getValue()));columns=Map.copyOf(frozen);
  index=NativeRampSourceIndex.prepare(footprint);bytes+=index.retainedBytes();beltCells=settings.escalator()==0?Set.of():NativeRampBeltGeometry.cells(footprint);var cells=new HashSet<>(index.cells());cells.addAll(beltCells);if(cells.size()>8192)throw new IllegalArgumentException("Ramp assembly cell budget");potential=Set.copyOf(cells);bytes+=96L*potential.size()+48L*columns.size();if(bytes>12L*1024*1024)throw new IllegalArgumentException("Ramp assembly retained budget");retainedBytes=bytes;
 }
 /** Snapshot tags are inspected only during construction and are never retained. */
 public static NativeRampAssembly prepare(BlockPos root,NativeRampSettings settings,NativeRampCapture.Plan plan,Map<BlockPos,CompoundTag> stagedSources){return new NativeRampAssembly(root,settings,plan,stagedSources);}
 public NativeRampFootprint.Spec footprint(){return footprint;}public NativeRampSettings settings(){return settings;}public BlockState original(){return original;}public Set<BlockPos> potential(){return potential;}public long retainedBytes(){return retainedBytes;}
 private int column(BlockPos pos){return footprint.facing().getAxis()==Direction.Axis.X?pos.getZ():pos.getX();}
 public Set<BlockPos> cells(double from,double to){if(!Double.isFinite(from)||!Double.isFinite(to)||from<0||from>1||to<0||to>1)throw new IllegalArgumentException("Ramp assembly pose");return com.vandorlabs.ramp.RampBeltPolicy.reservations(settings.escalator(),from,to)?beltCells:NativeRampFootprint.cells(footprint,from,to);}
 public NativeRampPartData part(BlockPos cell,NativeRampPartData.Motion motion,NativeRampPartData.Belt belt){
  if(!potential.contains(cell)||belt.mode()!=settings.escalator())throw new IllegalArgumentException("Ramp part outside prepared assembly");
  List<NativeRampPartData.Origin> selected;
  if(settings.escalator()!=0)selected=columns.getOrDefault(column(cell),List.of());else{var list=new ArrayList<NativeRampPartData.Origin>();for(var origin:index.origins(cell)){var source=sources.get(origin.position());if(source==null)throw new IllegalArgumentException("Unknown indexed Ramp source");list.add(source);}selected=List.copyOf(list);}
  var geometry=new ArrayList<NativeRampGeometry.Origin>();for(var origin:selected)geometry.add(origin.geometry());int row=footprint.verticalLayout()&&!footprint.moat()?cell.getY()-footprint.sourceY():cell.getX()*footprint.facing().getStepX()+cell.getZ()*footprint.facing().getStepZ()-footprint.minimumAlong();
  var spec=new NativeRampGeometry.Spec(cell,footprint.facing(),footprint.sourceY(),row,footprint.length(),footprint.treadPixels(),footprint.low(),footprint.high(),footprint.start(),footprint.end(),footprint.elevator(),footprint.travel(),footprint.extend(),footprint.fast(),footprint.interpolation(),footprint.verticalLayout(),footprint.moat(),original.getBlock() instanceof StairBlock,geometry);
  return new NativeRampPartData(spec,original,selected,motion,belt);
 }
}
