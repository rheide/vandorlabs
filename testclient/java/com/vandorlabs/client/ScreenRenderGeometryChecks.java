package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.render.*;
import com.vandorlabs.screen.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.renderer.BufferBuilder;

/** Actual released full-role positions/normals, independent of replacement role assembly. */
public final class ScreenRenderGeometryChecks {
 private static final class Draw {final int mode;final int[] points;Draw(int mode,int[] points){this.mode=mode;this.points=points;}}
 private static void emit(List<Draw> draws,BufferBuilder b,int mode,Runnable emit){b.begin(mode,BlockSurfaceFormat.get());emit.run();b.finishDrawing();int[] points=new int[b.getVertexCount()*4];for(int v=0;v<b.getVertexCount();v++){int at=v*BlockSurfaceFormat.get().getNextOffset();for(int i=0;i<3;i++)points[v*4+i]=b.getByteBuffer().getInt(at+i*4);points[v*4+3]=b.getByteBuffer().getInt(at+28);}draws.add(new Draw(mode,points));}
 public static void main(String[] args)throws Exception {
  JsonObject reference;try(InputStream in=new GZIPInputStream(new FileInputStream("testclient/fixtures/screen-role-geometry-reference.json.gz"))){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[4096];for(int n;(n=in.read(block))>=0;){bytes.write(block,0,n);if(bytes.size()>1024*1024)throw new AssertionError("Screen reference exceeds byte bound");}reference=new JsonParser().parse(new String(bytes.toByteArray(),"UTF-8")).getAsJsonObject();}
  BufferBuilder b=new BufferBuilder(65536);ScreenMeshEmission.Sink sink=(x,y,z,u,v,nx,ny,nz)->WorldSurface.vertex(b,x,y,z,0,0,nx,ny,nz);int profiles=0,vertices=0;
  for(JsonElement value:reference.getAsJsonArray("cases")){JsonObject row=value.getAsJsonObject();ScreenFamily family=ScreenFamily.fromId(row.get("id").getAsString());ScreenFamily.State state=family.decode(row.get("meta").getAsInt());int variant=row.get("variant").getAsInt();ScreenRenderGeometry geometry=ScreenRenderGeometry.of(family,state,variant==1,variant==1?2:0,variant==2,1);List<Draw> draws=new ArrayList<>();
   for(ScreenRenderGeometry.Body body:geometry.bodies){ScreenHousingMesh mesh=body.applyLayout?body.mesh.textureLayout(variant,1):body.mesh;if(mesh.quads.length>0)emit(draws,b,7,()->ScreenMeshEmission.housing(mesh.quads,body.ceiling,sink));if(mesh.triangles.length>0)emit(draws,b,4,()->ScreenMeshEmission.housing(mesh.triangles,body.ceiling,sink));}
   for(ScreenRenderGeometry.Surface surface:geometry.surfaces){if(variant==1&&surface.split){for(boolean top:new boolean[]{true,false}){ScreenSurface.Quad half=ScreenSurface.half(surface.quad,top);emit(draws,b,7,()->ScreenMeshEmission.surface(half,0,1,0,1,surface.ceiling,sink));}}else emit(draws,b,7,()->ScreenMeshEmission.surface(surface.quad,0,1,0,1,surface.ceiling,sink));}
   JsonArray expected=row.getAsJsonArray("draws");if(draws.size()!=expected.size())throw new AssertionError("Screen draw count "+family+" meta="+row.get("meta")+" variant="+variant+" expected="+expected.size()+" actual="+draws.size());
   for(int d=0;d<draws.size();d++){Draw actual=draws.get(d);JsonObject wanted=expected.get(d).getAsJsonObject();JsonArray points=wanted.getAsJsonArray("points");if(actual.mode!=wanted.get("mode").getAsInt()||actual.points.length!=points.size())throw new AssertionError("Screen draw topology "+family+" draw="+d);for(int i=0;i<actual.points.length;i++)if(actual.points[i]!=points.get(i).getAsInt())throw new AssertionError("Screen released geometry "+family+" meta="+row.get("meta")+" variant="+variant+" draw="+d+" value="+i+" expected="+points.get(i)+" actual="+actual.points[i]);vertices+=actual.points.length/4;}profiles++;
  }
  if(profiles!=210)throw new AssertionError("Screen geometry coverage "+profiles);System.out.println("Screen actual released role geometry PASS profiles="+profiles+" vertices="+vertices+" exact float positions/packed normals and quad/triangle order");
 }
}
