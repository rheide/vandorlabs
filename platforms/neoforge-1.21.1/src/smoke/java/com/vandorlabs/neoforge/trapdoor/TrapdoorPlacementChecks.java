package com.vandorlabs.neoforge.trapdoor;
import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
/** Compares native placement scalars with actual released specialized item/block callbacks. */
public final class TrapdoorPlacementChecks {
 private static void same(NativeTrapdoorPlacement.State state,JsonObject expected,String facing,String top,int profile){if(state.facing()!=Direction.from2DDataValue(expected.get(facing).getAsInt())||state.inverted()!=expected.get(top).getAsBoolean())throw new AssertionError("Trapdoor placement profile="+profile+" native="+state+" source="+expected);}
 public static void main(String[] args)throws Exception{int profiles=0;float[] hits={0,Math.nextDown(1/3F),1/3F,Math.nextUp(1/3F),.5F,Math.nextDown(2/3F),2/3F,Math.nextUp(2/3F),1};
  for(var element:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()){var r=element.getAsJsonObject();Direction side=Direction.from3DDataValue(r.get("side").getAsInt()),player=Direction.from2DDataValue(r.get("yaw").getAsInt());float y=hits[r.get("hit").getAsInt()];String input=r.get("input").getAsString();CompoundTag raw=input.isEmpty()?null:TagParser.parseTag(input);var state=r.get("family").getAsInt()==0?NativeTrapdoorPlacement.regular(side,player,y,false):NativeTrapdoorPlacement.diagonal(NativeTrapdoorPlacement.diagonalMode(raw,0),side,player,.1F,y,.9F,null,BlockPos.ZERO);same(state,r,"initialFacing","initialTop",profiles++);}
  if(profiles!=8640)throw new AssertionError("Complete specialized item placement corpus");
  for(String line:Files.readAllLines(Path.of(args[1]))){var r=JsonParser.parseString(line).getAsJsonObject();Direction side=Direction.from3DDataValue(r.get("side").getAsInt()),face=Direction.from2DDataValue(r.get("facing").getAsInt());String input=r.get("input").getAsString();CompoundTag raw=input.isEmpty()?null:TagParser.parseTag(input);int mode=NativeTrapdoorPlacement.diagonalMode(raw,r.get("mode").getAsInt());var clicked=new NativeTrapdoorPlacement.Support(r.get("mode").getAsInt(),face,r.get("inverted").getAsBoolean());var state=NativeTrapdoorPlacement.diagonal(mode,side,face,.1F,.7F,.9F,clicked,new BlockPos(side.getStepX(),side.getStepY(),side.getStepZ()));same(state,r,"initialFacing","initialTop",profiles++);}
  if(profiles!=12672)throw new AssertionError("Complete clicked support corpus");System.out.println("[vandorlabs][trapdoor-placement] CPU PASS actual specialized source8640 +clickedwall/trapdoor4032 placement scalars");
 }
}
