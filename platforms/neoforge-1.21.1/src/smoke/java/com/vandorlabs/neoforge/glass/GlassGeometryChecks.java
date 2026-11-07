package com.vandorlabs.neoforge.glass;

import com.google.gson.*;
import com.vandorlabs.render.PanelCornerGeometry;
import java.nio.file.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Actual discovered Source collision and contained-fluid replay, with no level or renderer. */
public final class GlassGeometryChecks {
 private static int checks;
 private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Glass geometry "+text);}
 private static void box(JsonArray expected,AABB b){double[] a={b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ};for(int i=0;i<6;i++)need(expected.get(i).getAsDouble()==a[i],"coordinate "+i);}
 private static double near(Direction f,int depth){double n=new double[]{6,0,12}[depth];return f==Direction.SOUTH||f==Direction.WEST?12-n:n;}
 private static Double arm(Direction facing,int profile){if(profile==0)return null;double n=near(profile<=3?facing.getClockWise():facing.getCounterClockWise(),(profile-1)%3);return profile<=3?12-n:n;}
 public static void main(String[] args)throws Exception {int profiles=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();int front=row.get("front").getAsInt(),back=row.get("back").getAsInt(),straight=row.get("straight").getAsInt();Direction facing=Direction.from3DDataValue(new int[]{3,4,2,5}[row.get("facing").getAsInt()]);
  var corner=front==0&&back==0?null:new PanelCornerGeometry.FlatCorner(arm(facing,front),arm(facing,back),front==0?null:front<=3,back==0?null:back<=3,(straight&1)!=0,(straight&2)!=0);
  var g=NativeGlassGeometry.build(facing,near(facing,row.get("depth").getAsInt()),corner);box(row.getAsJsonArray("bounds"),g.bounds());var expected=row.getAsJsonArray("collision");need(expected.size()==g.boxes().size(),"collision count");for(int i=0;i<expected.size();i++)box(expected.get(i).getAsJsonArray(),g.boxes().get(i));var cells=row.getAsJsonArray("cells");need(cells.size()==g.fluids().cells().size(),"fluid count");for(int i=0;i<cells.size();i++){var c=cells.get(i).getAsJsonObject();var actual=g.fluids().cells().get(i);box(c.getAsJsonArray("box"),actual.box());need(c.get("side").getAsInt()==actual.side(),"fluid side");}profiles++;
 }}need(profiles==2352,"source coverage");System.out.println("[vandorlabs][flat-glass-geometry] PASS profiles="+profiles+" fields="+checks);}
}
