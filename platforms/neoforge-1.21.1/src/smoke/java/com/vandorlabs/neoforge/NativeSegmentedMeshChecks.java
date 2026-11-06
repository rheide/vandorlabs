package com.vandorlabs.neoforge;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.segmented.SegmentedPanelTransform;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
public final class NativeSegmentedMeshChecks {
 public static void main(String[] args)throws Exception{
  var rows=Files.readAllLines(Path.of(args[0]));var encoded=JsonParser.parseString(rows.getFirst()).getAsJsonObject().getAsJsonArray("source");float[] source=new float[encoded.size()];for(int i=0;i<source.length;i++)source[i]=Float.intBitsToFloat(encoded.get(i).getAsInt());float[] frozen=source.clone();var mesh=new NativeSegmentedMesh(source);int profiles=0,vertices=0;double[] scratch=new double[6];
  for(String line:rows.subList(1,rows.size())){var row=JsonParser.parseString(line).getAsJsonObject();double[] pose=new double[6];for(int i=0;i<6;i++)pose[i]=row.getAsJsonArray("pose").get(i).getAsDouble();int rotation=row.get("rotation").getAsInt();var expected=row.getAsJsonArray("vertices");
   for(int v=0;v<mesh.vertices();v++){SegmentedPanelTransform.vertex(source,v*8,pose,rotation,1.25,-2.5,3.75,scratch);for(int a=0;a<3;a++)need(Float.floatToRawIntBits((float)scratch[a])==expected.get(v*8+a).getAsInt(),"actual Forge position");int normal=0;for(int a=0;a<3;a++)normal|=(((byte)((float)scratch[a+3]*127))&255)<<(a*8);need(normal==(expected.get(v*8+7).getAsInt()&0xffffff),"actual Forge normal");need(expected.get(v*8+3).getAsInt()==0x67ffffff,"source RGBA");need(expected.get(v*8+6).getAsInt()==0x00c00070,"source light order");}
   int[] vertex={-1},calls={0};VertexConsumer consumer=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,values)->{
    if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,values);
    switch(method.getName()){
     case "addVertex":SegmentedPanelTransform.vertex(source,++vertex[0]*8,pose,rotation,0,0,0,scratch);for(int a=0;a<3;a++)need(Float.floatToRawIntBits((Float)values[a])==Float.floatToRawIntBits((float)scratch[a]),"native position");break;
     case "setColor":need(Arrays.equals(values,new Object[]{255,255,255,103}),"native RGBA");break;
     case "setUv":int at=vertex[0]*8;need((Float)values[0]==source[at+3]&&(Float)values[1]==source[at+4],"native UV");break;
     case "setLight":need((Integer)values[0]==0x00c00070,"native packed light");break;
     case "setOverlay":need((Integer)values[0]==123,"native overlay");break;
     case "setNormal":for(int a=0;a<3;a++)need(Math.abs((Float)values[a]-(float)scratch[a+3])<1e-6,"native normal");break;
    }calls[0]++;return method.getReturnType()==VertexConsumer.class?proxy:null;
   });mesh.emit(new PoseStack().last(),consumer,pose,rotation,0x00c00070,123,103);need(vertex[0]+1==mesh.vertices()&&calls[0]==mesh.vertices()*6,"complete native records");profiles++;vertices+=mesh.vertices();
  }need(Arrays.equals(source,frozen),"immutable source");need(profiles==960,"complete oracle");System.out.println("[vandorlabs][segmented-mesh] CPU PASS profiles="+profiles+" vertices="+vertices+" exact Forge scalar position/normals; native RGBA/UV/light/overlay immutable mesh reused");
 }
 private static void need(boolean value,String label){if(!value)throw new AssertionError(label);}
}
