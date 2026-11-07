package com.vandorlabs.neoforge.ramp;
import com.google.gson.*;
import com.vandorlabs.ramp.ControllerPlatform;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.AABB;
/** Released generated reservation coordinates, rather than a union of rendered boxes. */
public final class NativeRampFootprintChecks {
 public static void main(String[] args)throws Exception{int rows=0,sets=0,cells=0;try(var lines=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0])))))){String line;while((line=lines.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();int index=0;for(var stateElement:row.getAsJsonArray("states")){var state=stateElement.getAsJsonObject();var tag=TagParser.parseTag(state.get("saved").getAsString());if(!tag.getList("Sources",Tag.TAG_COMPOUND).isEmpty()){
   var sources=new ArrayList<NativeRampGeometry.Origin>();for(var element:tag.getList("Sources",Tag.TAG_COMPOUND)){var p=(CompoundTag)element;List<AABB> shape=row.get("material").getAsInt()==3?List.of(new AABB(0,0,0,1,.5,1),new AABB(0,.5,0,1,1,.5)):List.of(new AABB(0,tag.getDouble("Low"),0,1,tag.getDouble("High"),1));sources.add(new NativeRampGeometry.Origin(new BlockPos(p.getInt("X"),p.getInt("Y"),p.getInt("Z")),shape));}
   var spec=new NativeRampFootprint.Spec(new BlockPos(4,80,4),Direction.from2DDataValue(tag.getInt("Facing")),tag.getBoolean("RampVerticalLayout")&&!tag.getBoolean("MoatMode")?tag.getInt("MinAlong"):sources.stream().mapToInt(s->s.position().getY()).min().orElseThrow(),tag.getInt("MinAlong"),tag.getInt("Length"),tag.getDouble("Low"),tag.getDouble("High"),tag.getInt("StartOffsetHalfSteps")/2D,tag.getInt("EndOffsetHalfSteps")/2D,tag.getInt("TreadPixels"),tag.getBoolean("Elevator"),tag.getInt("TravelAxis"),tag.getBoolean("ExtendSegments"),tag.getInt("SpeedMode")==0,tag.getInt("RampInterpolation"),tag.getBoolean("RampVerticalLayout"),tag.getBoolean("MoatMode"),sources);
   double from=state.get("pose").getAsDouble(),to=!tag.getBoolean("Moving")?from:ControllerPlatform.pose(tag.getDouble("StartPose"),tag.contains("RampTargetPose")?tag.getDouble("RampTargetPose"):tag.getBoolean("Open")?1:0,tag.getLong("LastStepTick")-tag.getLong("StartTick")+1,tag.getInt("Duration"));var actual=NativeRampFootprint.cells(spec,from,to);var expected=new HashSet<BlockPos>();for(var element:tag.getList("Cells",Tag.TAG_COMPOUND)){var p=(CompoundTag)element;expected.add(new BlockPos(p.getInt("X"),p.getInt("Y"),p.getInt("Z")));}if(!actual.equals(expected))throw new AssertionError("Ramp footprint row="+rows+" state="+index+" from="+from+" to="+to+" expected="+expected+" actual="+actual);sets++;cells+=actual.size();
  }index++;}rows++;}}
  boolean refused=false;try{NativeRampFootprint.cells(new NativeRampFootprint.Spec(BlockPos.ZERO,Direction.NORTH,0,0,1,0,1,0,-2,8,false,0,false,false,0,false,false,List.of(new NativeRampGeometry.Origin(new BlockPos(100,0,0),List.of(new AABB(0,0,0,1,1,1))))),0,1);}catch(IllegalArgumentException expected){refused=true;}if(!refused)throw new AssertionError("Unbounded Ramp origin admitted");if(rows!=768||sets==0)throw new AssertionError("Ramp footprint coverage");System.out.println("Native Ramp footprint PASS Source="+rows+" reservationSets="+sets+" cells="+cells+" no world mutation");
 }
}
