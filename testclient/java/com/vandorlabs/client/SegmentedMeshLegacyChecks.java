package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.segmented.SegmentedDoorGeometry;
import java.io.*;
import net.minecraft.client.renderer.BufferBuilder;
/** Freeze the actual released section emitter before extracting its transform. */
public final class SegmentedMeshLegacyChecks {
 public static void main(String[] args)throws Exception{
  File target=new File(args[0]);target.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int cases=0,vertices=0;double[] pose=new double[6];
  float h=1.5F;float[][][] faces={{{0,0,h},{16,0,h},{16,16,h},{0,16,h}},{{16,0,-h},{0,0,-h},{0,16,-h},{16,16,-h}},{{0,0,-h},{0,0,h},{0,16,h},{0,16,-h}},{{16,0,h},{16,0,-h},{16,16,-h},{16,16,h}},{{0,0,h},{0,0,-h},{16,0,-h},{16,0,h}},{{0,16,-h},{0,16,h},{16,16,h},{16,16,-h}}};int[][] normals={{0,0,1},{0,0,-1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
  StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();JsonArray source=new JsonArray();for(int face=0;face<6;face++)for(int v=0;v<4;v++){float u=v==1||v==2?.8125F:.1875F,w=v<2?.875F:.125F;float[] p=faces[face][v];capture.pos(p[0],p[1],p[2]).tex(u,w).normal(normals[face][0],normals[face][1],normals[face][2]).endVertex();for(float f:new float[]{p[0],p[1],p[2],u,w,normals[face][0],normals[face][1],normals[face][2]})source.add(Float.floatToRawIntBits(f));}StaticSurfaceMesh mesh=capture.finish();
  try(PrintWriter out=new PrintWriter(target,"UTF-8")){
   JsonObject header=new JsonObject();header.add("source",source);out.println(gson.toJson(header));
   for(int height:new int[]{1,3,17})for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(int rotation=0;rotation<4;rotation++)for(double progress:new double[]{0,.0625,.375,.75,1})for(int row:new int[]{0,height-1}){
    SegmentedDoorGeometry.panel(row,height,1,3,progress,direction,straight,pose);pose[2]+=-13D/32;BufferBuilder b=new BufferBuilder(4096);b.begin(7,BlockSurfaceFormat.get());mesh.drawSection(b,1.25,-2.5,3.75,pose,rotation,0x00c00070,103);b.finishDrawing();JsonObject record=new JsonObject();JsonArray state=new JsonArray();for(double value:pose)state.add(value);record.add("pose",state);record.addProperty("rotation",rotation);JsonArray data=new JsonArray();for(int v=0;v<b.getVertexCount();v++)for(int a=0;a<8;a++)data.add(b.getByteBuffer().getInt(v*32+a*4));record.add("vertices",data);out.println(gson.toJson(record));cases++;vertices+=b.getVertexCount();
   }
  }SegmentedReferenceGolden.check(target,"6c85710d67e0217192b632fd63eb245374b2a3670fea6882d0ff784262e3a63d");System.out.println("Segmented mesh actual Forge PASS profiles="+cases+" vertices="+vertices+" complete packed position/color/UV/light/normal records");
 }
}
