package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.render.*;
import com.vandorlabs.screen.*;
import com.vandorlabs.segmented.SegmentedPanelMesh;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Active native consumer path against released Source positions, normals and GL transforms. */
public final class NativeScreenMeshChecks {
 private static void need(boolean yes,String why){if(!yes)throw new AssertionError(why);}
 private static JsonObject read(Path path,boolean compressed)throws Exception{try(InputStream raw=Files.newInputStream(path);InputStream in=compressed?new GZIPInputStream(raw):raw){byte[] bytes=in.readNBytes(1024*1024+1);need(bytes.length<=1024*1024,"bounded Screen fixture");return JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}}
 public static void main(String[] args)throws Exception{
  var poses=new HashMap<String,JsonArray>();for(var value:read(Path.of(args[1]),false).getAsJsonArray("poses")){var row=value.getAsJsonObject();poses.put(key(row),row.getAsJsonArray("modelview"));}
  int profiles=0,vertices=0;var texture=new SegmentedPanelMesh.Texture(){public float u(double p){return (float)p;}public float v(double p){return (float)p;}};
  for(var value:read(Path.of(args[0]),true).getAsJsonArray("cases")){
   var row=value.getAsJsonObject();var family=ScreenFamily.fromId(row.get("id").getAsString());int variant=row.get("variant").getAsInt();var state=family.decode(row.get("meta").getAsInt());var geometry=ScreenRenderGeometry.of(family,state,variant==1,variant==1?2:0,variant==2,1);double[] pose=new double[16];ScreenRenderPose.write(family,state,variant==2,1,pose);var expectedPose=poses.get(key(row));need(expectedPose!=null,"Source pose present");List<NativeScreenMesh> meshes=new ArrayList<>();
   for(var body:geometry.bodies){var mesh=body.applyLayout?body.mesh.textureLayout(variant,1):body.mesh;if(mesh.quads.length>0)meshes.add(NativeScreenMesh.build(7,pose,family==ScreenFamily.HALF_DIAGONAL,texture,sink->ScreenMeshEmission.housing(mesh.quads,body.ceiling,sink)));if(mesh.triangles.length>0)meshes.add(NativeScreenMesh.build(4,pose,family==ScreenFamily.HALF_DIAGONAL,texture,sink->ScreenMeshEmission.housing(mesh.triangles,body.ceiling,sink)));}
   for(var surface:geometry.surfaces){if(variant==1&&surface.split){for(boolean top:new boolean[]{true,false}){var half=ScreenSurface.half(surface.quad,top);meshes.add(NativeScreenMesh.build(7,pose,family==ScreenFamily.HALF_DIAGONAL,texture,sink->ScreenMeshEmission.surface(half,0,1,0,1,surface.ceiling,sink)));}}else meshes.add(NativeScreenMesh.build(7,pose,family==ScreenFamily.HALF_DIAGONAL,texture,sink->ScreenMeshEmission.surface(surface.quad,0,1,0,1,surface.ceiling,sink)));}
   var draws=row.getAsJsonArray("draws");need(meshes.size()==draws.size(),"released draw count");
   for(int d=0;d<meshes.size();d++){var expected=draws.get(d).getAsJsonObject();var points=expected.getAsJsonArray("points");boolean triangles=expected.get("mode").getAsInt()==4;int[] emitted={0};String label=key(row)+" draw="+d;var target=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,values)->{if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,values);int index=emitted[0]-1;int source=triangles?(index/4*3+Math.min(index%4,2)):index;int at=source*4;
     switch(method.getName()){
      case "addVertex"->{index=emitted[0]++;source=triangles?(index/4*3+Math.min(index%4,2)):index;at=source*4;for(int axis=0;axis<3;axis++){double wanted=f(expectedPose,12+axis);for(int c=0;c<3;c++)wanted+=f(expectedPose,c*4+axis)*Float.intBitsToFloat(points.get(at+c).getAsInt());need(Math.abs((Float)values[axis]-wanted)<.000002,label+" Source GL position axis="+axis);}}
      case "setNormal"->{int packed=points.get(at+3).getAsInt();for(int axis=0;axis<3;axis++){double wanted=0;for(int c=0;c<3;c++){double scale=family==ScreenFamily.HALF_DIAGONAL&&c>0?32:16;wanted+=f(expectedPose,c*4+axis)*scale*((byte)(packed>>>(c*8))/127F);}need((byte)((Float)values[axis]*127F)==(byte)Math.round(wanted*127),label+" exact Source packed normal axis="+axis);}}
      case "setColor"->{for(Object n:values)need((Integer)n==255,"white color");}
      case "setLight"->need((Integer)values[0]==0xA00070,"explicit asymmetric light");
      case "setOverlay"->need((Integer)values[0]==123,"explicit overlay");
     }return method.getReturnType()==VertexConsumer.class?proxy:null;
    });var mesh=meshes.get(d);mesh.emit(new PoseStack().last(),target,0xA00070,123,0,1);need(emitted[0]==mesh.vertices(),"complete active consumer");need(mesh.vertices()==points.size()/4/(triangles?3:4)*4,"quad-safe Source topology");need(mesh.retainedBytes()==48L+mesh.vertices()*32L,"byte accounting");vertices+=emitted[0];
   }profiles++;
  }
  double[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};checkPlayback(identity,texture);boolean bounded=false;try{NativeScreenMesh.build(7,identity,false,texture,sink->{for(int i=0;i<4100;i++)sink.vertex(0,0,0,0,0,0,1,0);});}catch(IllegalStateException expected){bounded=true;}need(bounded,"oversized mesh fails explicitly");need(profiles==210,"all seven released roles");System.out.println("[vandorlabs][screen-mesh] CPU PASS profiles="+profiles+" vertices="+vertices+" actual Source GL position/normal/topology, bounded packed storage and active VertexConsumer dispatch");
 }
 private static void checkPlayback(double[] identity,SegmentedPanelMesh.Texture texture){
  var mesh=NativeScreenMesh.build(4,identity,false,texture,sink->{for(int i=0;i<3;i++)sink.vertex(i,0,0,i/2D,i/2D,0,1,0);});int[] at={-1};var pose=new PoseStack();pose.translate(2,3,4);
  var target=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,values)->{if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,values);if(method.getName().equals("addVertex")){int source=Math.min(++at[0],2);need((Float)values[0]==source+2&&(Float)values[1]==3&&(Float)values[2]==4,"caller pose applied once");}if(method.getName().equals("setUv")){float unit=Math.min(at[0],2)/2F;need((Float)values[0]==unit&&(Float)values[1]==.25F+.125F*unit,"strip frame maps normalized UV without changing U");}return method.getReturnType()==VertexConsumer.class?proxy:null;});mesh.emit(pose.last(),target,0,0,.25,.125);need(at[0]==3,"triangle becomes one quad with duplicate third vertex");
 }
 private static String key(JsonObject row){return row.get("id").getAsString()+":"+row.get("meta").getAsInt()+":"+row.get("variant").getAsInt();}
 private static float f(JsonArray array,int at){return Float.intBitsToFloat(array.get(at).getAsInt());}
}
