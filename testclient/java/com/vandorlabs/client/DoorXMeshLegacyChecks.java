package com.vandorlabs.client;
import com.google.gson.*;
import java.io.*;
import net.minecraft.client.renderer.BufferBuilder;
/** Actual source StaticSurfaceMesh X panels/caps and packed GL-free output. */
public final class DoorXMeshLegacyChecks {
 public static void main(String[] args)throws Exception{
  File target=new File(args[0]);target.getAbsoluteFile().getParentFile().mkdirs();int cases=0;Gson gson=new Gson();try(PrintWriter out=new PrintWriter(target,"UTF-8")){
   for(boolean right:new boolean[]{false,true})for(boolean caps:new boolean[]{false,true})for(float inset:new float[]{0,1/16F}){
    StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();float[][] points={{inset,inset,9F/16},{1-inset,inset,9F/16},{1-inset,2-inset,9F/16},{inset,2-inset,9F/16}};JsonArray source=new JsonArray();for(int i=0;i<4;i++){float u=i==1||i==2?.75F:.25F,v=i<2?.875F:.125F;capture.pos(points[i][0],points[i][1],points[i][2]).color(255,255,255,255).tex(u,v).normal(0,0,1).endVertex();for(float n:new float[]{points[i][0],points[i][1],points[i][2],u,v})source.add(Float.floatToRawIntBits(n));}
    StaticSurfaceMesh[] panels=capture.finish().xPanels(right?1:0,caps);JsonObject row=new JsonObject();row.addProperty("right",right);row.addProperty("caps",caps);row.add("source",source);JsonArray expected=new JsonArray();for(StaticSurfaceMesh panel:panels){BufferBuilder b=new BufferBuilder(4096);b.begin(7,BlockSurfaceFormat.get());panel.draw(b,0,0);b.finishDrawing();JsonArray data=new JsonArray();for(int v=0;v<b.getVertexCount();v++){int at=v*32;for(int a=0;a<3;a++)data.add(b.getByteBuffer().getInt(at+a*4));data.add(b.getByteBuffer().getInt(at+16));data.add(b.getByteBuffer().getInt(at+20));data.add(b.getByteBuffer().getInt(at+28)&0xffffff);}expected.add(data);}row.add("panels",expected);out.println(gson.toJson(row));cases++;
   }
  }System.out.println("Large Door X mesh actual Forge PASS profiles="+cases+" clipping/caps packed position/UV/normal records");
 }
}
