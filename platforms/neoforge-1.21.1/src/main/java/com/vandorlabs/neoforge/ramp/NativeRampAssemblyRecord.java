package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Bounded recipe metadata. Source states and private snapshots remain in the journal before-images. */
final class NativeRampAssemblyRecord {
 record Saved(NativeRampSettings settings,NativeRampCapture.Plan capture){}
 private NativeRampAssemblyRecord(){}
 static CompoundTag write(NativeRampSettings settings,NativeRampCapture.Plan capture){
  var tag=new CompoundTag();tag.putInt("Schema",1);var choices=new CompoundTag();settings.writeChoices(choices);tag.put("Choices",choices);
  tag.putInt("Facing",capture.facing().get3DDataValue());tag.putInt("Minimum",capture.minimumAlong());tag.putInt("Length",capture.length());tag.putInt("SourceY",capture.sourceY());tag.putDouble("Low",capture.low());tag.putDouble("High",capture.high());
  var sources=new ListTag();long boxes=0;for(var source:capture.sources()){var row=new CompoundTag();row.putIntArray("Position",new int[]{source.position().getX(),source.position().getY(),source.position().getZ()});var shapes=new ListTag();for(var box:source.shapes()){var coordinates=new ListTag();for(double value:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})coordinates.add(DoubleTag.valueOf(value));shapes.add(coordinates);if(++boxes>32768)throw new IllegalArgumentException("Ramp recipe shape budget");}row.put("Shapes",shapes);sources.add(row);}tag.put("Sources",sources);
  if(sources.isEmpty()||sources.size()>128||tag.sizeInBytes()>2*1024*1024)throw new IllegalArgumentException("Ramp recipe record budget");return tag;
 }
 static Saved read(CompoundTag tag,BlockPos root,Map<BlockPos,BlockState> states){
  if(!tag.contains("Schema",Tag.TAG_INT)||tag.getInt("Schema")!=1||!tag.contains("Choices",Tag.TAG_COMPOUND)||tag.sizeInBytes()>2*1024*1024)throw new IllegalArgumentException("Unknown Ramp recipe record");
  var choices=tag.getCompound("Choices");var settings=NativeRampSettings.read(choices);var canonical=new CompoundTag();settings.writeChoices(canonical);if(!canonical.equals(choices))throw new IllegalArgumentException("Noncanonical Ramp recipe choices");
  for(String key:new String[]{"Facing","Minimum","Length","SourceY"})if(!tag.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Ramp recipe integer");for(String key:new String[]{"Low","High"})if(!tag.contains(key,Tag.TAG_DOUBLE))throw new IllegalArgumentException("Ramp recipe boundary");
  int face=tag.getInt("Facing"),length=tag.getInt("Length");double low=tag.getDouble("Low"),high=tag.getDouble("High");if(face<2||face>5||length<1||length>128||!Double.isFinite(low)||!Double.isFinite(high)||low<0||high>1||low>=high)throw new IllegalArgumentException("Ramp recipe dimensions");
  if(!tag.contains("Sources",Tag.TAG_LIST))throw new IllegalArgumentException("Ramp recipe sources");var rows=tag.getList("Sources",Tag.TAG_COMPOUND);if(rows.isEmpty()||rows.size()>128)throw new IllegalArgumentException("Ramp recipe source budget");var sources=new ArrayList<NativeRampCapture.Source>();var seen=new HashSet<BlockPos>();int boxes=0;
  for(var value:rows){var row=(CompoundTag)value;if(!row.contains("Position",Tag.TAG_INT_ARRAY)||row.getIntArray("Position").length!=3||!row.contains("Shapes",Tag.TAG_LIST))throw new IllegalArgumentException("Ramp recipe source fields");var xyz=row.getIntArray("Position");var pos=new BlockPos(xyz[0],xyz[1],xyz[2]);var state=states.get(pos);if(state==null||!seen.add(pos)||Math.abs((long)pos.getX()-root.getX())>64||Math.abs((long)pos.getY()-root.getY())>64||Math.abs((long)pos.getZ()-root.getZ())>64)throw new IllegalArgumentException("Ramp recipe source identity");var shapes=new ArrayList<AABB>();var packed=row.getList("Shapes",Tag.TAG_LIST);if(packed.isEmpty()||packed.size()>256)throw new IllegalArgumentException("Ramp recipe shape count");
   for(var shape:packed){if(!(shape instanceof ListTag numbers)||numbers.getElementType()!=Tag.TAG_DOUBLE||numbers.size()!=6||++boxes>32768)throw new IllegalArgumentException("Ramp recipe coordinates");double[] c=new double[6];for(int i=0;i<6;i++){c[i]=numbers.getDouble(i);if(!Double.isFinite(c[i])||c[i]<0||c[i]>1)throw new IllegalArgumentException("Ramp recipe coordinate bounds");}if(c[0]>=c[3]||c[1]>=c[4]||c[2]>=c[5])throw new IllegalArgumentException("Ramp recipe empty shape");shapes.add(new AABB(c[0],c[1],c[2],c[3],c[4],c[5]));}
   sources.add(new NativeRampCapture.Source(pos,state,shapes));
  }
  if(!seen.equals(states.keySet()))throw new IllegalArgumentException("Ramp recipe source set");int minimum=Integer.MAX_VALUE,maximum=Integer.MIN_VALUE,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;var facing=Direction.from3DDataValue(face);for(var source:sources){var p=source.position();int along=settings.verticalLayout()&&!settings.moat()?p.getY():p.getX()*facing.getStepX()+p.getZ()*facing.getStepZ();minimum=Math.min(minimum,along);maximum=Math.max(maximum,along);minY=Math.min(minY,p.getY());maxY=Math.max(maxY,p.getY());}if(tag.getInt("Minimum")!=minimum||length!=(settings.moat()?maxY-minY+1:maximum-minimum+1)||tag.getInt("SourceY")!=(settings.verticalLayout()&&!settings.moat()?minimum:minY))throw new IllegalArgumentException("Ramp recipe extent mismatch");var capture=new NativeRampCapture.Plan(sources,Direction.from3DDataValue(face),tag.getInt("Minimum"),length,tag.getInt("SourceY"),low,high);if(!write(settings,capture).equals(tag))throw new IllegalArgumentException("Unknown Ramp recipe fields");return new Saved(settings,capture);
 }
}
