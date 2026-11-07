package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.render.*;
import com.vandorlabs.screen.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.renderer.BufferBuilder;

/** Independent assembly proof against whole released GL draws; tray meshes remain separate. */
public final class ScreenChipBodyChecks {
 private static int expectedMode=7;private static boolean compareUv;
 private static boolean matches(JsonObject draw,BufferBuilder b,double[] pose){
  if(draw.get("mode").getAsInt()!=expectedMode||draw.get("vertexCount").getAsInt()!=b.getVertexCount())return false;
  JsonArray matrix=draw.getAsJsonArray("modelview"),data=draw.getAsJsonArray("vertices");int stride=draw.get("stride").getAsInt()/4;
  for(int i=0;i<16;i++)if(Math.abs(pose[i]-Float.intBitsToFloat(matrix.get(i).getAsInt()))>2e-7)return false;
  for(int v=0;v<b.getVertexCount();v++)for(int i=0;i<3;i++)if(b.getByteBuffer().getInt(v*BlockSurfaceFormat.get().getNextOffset()+i*4)!=data.get(v*stride+i).getAsInt())return false;
  for(int v=0;v<b.getVertexCount();v++)if((b.getByteBuffer().getInt(v*BlockSurfaceFormat.get().getNextOffset()+28)&0xffffff)!=(data.get(v*stride+7).getAsInt()&0xffffff))return false;
  if(compareUv)for(int v=0;v<b.getVertexCount();v++)for(int i=4;i<6;i++)if(b.getByteBuffer().getInt(v*BlockSurfaceFormat.get().getNextOffset()+i*4)!=data.get(v*stride+i).getAsInt())return false;
  return true;
 }
 public static void main(String[] args)throws Exception{
  BufferBuilder b=new BufferBuilder(65536);ScreenMeshEmission.Sink sink=(x,y,z,u,v,nx,ny,nz)->WorldSurface.vertex(b,x,y,z,u,v,nx,ny,nz);int profiles=0,bodies=0,decks=0;
  try(BufferedReader in=new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream("testclient/fixtures/screen-chip-body-gl.jsonl.gz")),"UTF-8"))){for(String line;(line=in.readLine())!=null;){JsonObject row=new JsonParser().parse(line).getAsJsonObject();ScreenFamily family=ScreenFamily.fromId(row.get("id").getAsString());ScreenFamily.State state=family.decode(row.get("meta").getAsInt());int variant=row.get("variant").getAsInt();ScreenRenderGeometry geometry=ScreenRenderGeometry.of(family,state,variant==1,variant==1?2:0,variant==2,1,true);
   for(ScreenRenderGeometry.Body body:geometry.bodies){double[] pose=new double[16];ScreenRenderPose.write(body.basePose?ScreenFamily.HALF_CONSOLE:family,body.basePose?new ScreenFamily.State(state.facing,false,false,false):state,body.basePose?false:variant==2,1,pose);ScreenHousingMesh mesh=body.applyLayout?body.mesh.textureLayout(variant,1):body.mesh;b.begin(7,BlockSurfaceFormat.get());ScreenMeshEmission.housing(mesh.quads,body.ceiling,sink);b.finishDrawing();boolean found=false;for(JsonElement value:row.getAsJsonArray("draws"))found|=matches(value.getAsJsonObject(),b,pose);if(!found)throw new AssertionError("Source CHIP body mismatch "+family+" meta="+row.get("meta")+" variant="+variant+" base="+body.basePose);bodies++;if(mesh.triangles.length>0){expectedMode=4;b.begin(4,BlockSurfaceFormat.get());ScreenMeshEmission.housing(mesh.triangles,body.ceiling,sink);b.finishDrawing();found=false;for(JsonElement value:row.getAsJsonArray("draws"))found|=matches(value.getAsJsonObject(),b,pose);if(!found)throw new AssertionError("Source CHIP triangular cap mismatch "+family);expectedMode=7;bodies++;}}
   if(family!=ScreenFamily.HALF_DIAGONAL&&variant!=2){compareUv=variant==0;double[] pose=new double[16];ScreenRenderPose.write(family,state,false,1,pose);b.begin(7,BlockSurfaceFormat.get());ScreenMeshEmission.surface(ScreenRenderGeometry.chipDeck(),1,ScreenRenderGeometry.deckEnd(1,0),0,1,false,sink);b.finishDrawing();boolean found=false;for(JsonElement value:row.getAsJsonArray("draws"))found|=matches(value.getAsJsonObject(),b,pose);if(!found)throw new AssertionError("Source CHIP deck mismatch "+family+" meta="+row.get("meta"));decks++;compareUv=false;}profiles++;
  }}if(profiles!=432)throw new AssertionError("CHIP body coverage "+profiles);System.out.println("Screen Source CHIP bodies PASS profiles="+profiles+" bodies="+bodies+" insetDecks="+decks);
 }
}
