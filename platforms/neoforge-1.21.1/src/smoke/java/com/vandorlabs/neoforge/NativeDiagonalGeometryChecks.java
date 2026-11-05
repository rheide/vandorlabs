package com.vandorlabs.neoforge;
import com.google.gson.*;
import com.vandorlabs.neoforge.shape.*;
import java.nio.file.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
/** Compare actual Forge outputs with native product geometry; no reference algorithm is copied. */
public final class NativeDiagonalGeometryChecks {
 static long coordinates,boxes,faces;static String key;
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(key+" "+message);}
 static boolean bool(JsonObject o,String k){return o.has(k)&&o.get(k).getAsBoolean();}
 static Double nullable(JsonObject o,String k){return !o.has(k)||o.get(k).isJsonNull()?null:o.get(k).getAsDouble();}
 static Boolean option(JsonObject o,String k){return !o.has(k)||o.get(k).isJsonNull()?null:o.get(k).getAsBoolean();}
 static void same(JsonArray want,String label,double... actual){require(want.size()==actual.length,label+" count");for(int i=0;i<actual.length;i++){coordinates++;double v=want.get(i).getAsDouble();require(Double.doubleToRawLongBits(v)==Double.doubleToRawLongBits(actual[i]),label+" coordinate"+i+" expected="+v+" actual="+actual[i]);}}
 static void box(JsonArray want,AABB b,String label){same(want,label,b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
 public static void main(String[] args)throws Exception{
  JsonObject root;try(var r=Files.newBufferedReader(Paths.get(System.getProperty("vandorlabs.diagonalReference")))){root=JsonParser.parseReader(r).getAsJsonObject();}key="oracle";require(root.get("schema").getAsInt()==1&&root.get("golden").getAsString().equals("e5e8e78899db52f45e7c3f74888b9b39eb8e6dc1f92722206c24beedee9e2062"),"frozen Forge reference identity");int cases=0;
  for(var e:root.getAsJsonArray("cases")){var row=e.getAsJsonObject();key="case"+cases+" face="+row.get("facing")+" inverted="+row.get("inverted")+" mode="+row.get("mode")+" fill="+row.get("fill")+" profile="+row.get("profile")+" join="+row.get("joinChoice");
   NativeDiagonalWallGeometry.Join join=null;if(row.has("corner")&&!row.get("corner").isJsonNull()){var c=row.getAsJsonObject("corner");join=new NativeDiagonalWallGeometry.Join(option(c,"frontRight"),option(c,"backRight"),bool(c,"straightLeft"),bool(c,"straightRight"),nullable(c,"frontFlat"),nullable(c,"backFlat"));}
   var cj=row.getAsJsonArray("clip");double[] clip=new double[cj.size()];for(int i=0;i<clip.length;i++)clip[i]=cj.get(i).getAsDouble();Double lower=nullable(row,"lower"),upper=nullable(row,"upper");
   var pose=new NativeDiagonalWallGeometry.Pose(Direction.byName(row.get("facing").getAsString()),bool(row,"inverted"),row.get("mode").getAsInt(),row.get("fill").getAsInt(),join,lower==null?Double.NaN:lower,upper==null?Double.NaN:upper,bool(row,"hideLower"),bool(row,"hideUpper"),new NativeDiagonalWallGeometry.Clip(clip),NativePanelLayout.UNIT);
   var geometry=NativeDiagonalWallGeometry.build(pose);var expected=row.getAsJsonArray("boxes");require(expected.size()==geometry.boxes().size(),"collision count expected="+expected.size()+" actual="+geometry.boxes().size());for(int i=0;i<expected.size();i++){box(expected.get(i).getAsJsonArray(),geometry.boxes().get(i),"box"+i);boxes++;}box(row.getAsJsonArray("selection"),geometry.selectionBounds(),"selection");
   var mesh=geometry.fluidRegions(bool(row,"inset")).surfaces();expected=row.getAsJsonArray("faces");require(expected.size()==mesh.size(),"faces count expected="+expected.size()+" actual="+mesh.size());for(int i=0;i<expected.size();i++){var f=expected.get(i).getAsJsonObject();var a=mesh.get(i);require(f.get("side").getAsInt()==a.side&&f.get("axis").getAsInt()==a.axis&&f.get("boundary").getAsInt()==a.boundary,"face metadata"+i);same(f.getAsJsonArray("normal"),"normal"+i,a.nx,a.ny,a.nz);var vertices=f.getAsJsonArray("vertices");require(vertices.size()==a.vertices.size(),"vertex count");for(int n=0;n<vertices.size();n++){var p=a.vertices.get(n);same(vertices.get(n).getAsJsonArray(),"face"+i+" vertex"+n,p.x,p.y,p.z);}faces++;}cases++;
  }
  System.out.println("Native diagonal Forge reference PASS cases="+cases+" boxes="+boxes+" faces="+faces+" raw-coordinates="+coordinates+" golden="+root.get("golden"));
 }
}
