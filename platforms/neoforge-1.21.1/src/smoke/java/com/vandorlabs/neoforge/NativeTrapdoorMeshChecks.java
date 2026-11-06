package com.vandorlabs.neoforge;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.render.*;
import com.vandorlabs.segmented.SegmentedPanelMesh;
import java.nio.file.*;
import java.lang.reflect.*;
/** Actual released byte records through native active-consumer dispatch. */
public final class NativeTrapdoorMeshChecks {
 public static void main(String[] args)throws Exception{
  int profiles=0;long vertices=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){var header=JsonParser.parseString(reader.readLine()).getAsJsonObject();double[] borders=new double[4];for(int i=0;i<4;i++)borders[i]=header.getAsJsonArray("borders").get(i).getAsDouble();double aspect=header.get("frameAspect").getAsDouble();
   for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();int shape=row.get("shape").getAsInt(),turn=row.get("turn").getAsInt(),layout=row.get("layout").getAsInt(),variant=row.get("variant").getAsInt();boolean inverted=row.get("inverted").getAsBoolean(),sliding=row.get("sliding").getAsBoolean();double pose=row.get("pose").getAsDouble();double[][] corners=shape==0?TrapdoorGeometry.corners(1,sliding,turn,pose):DiagonalTrapdoorGeometry.corners(shape-1,inverted,turn,sliding,inverted,pose);int first=shape==1||shape==2?2:0,v=first==0?2:1;double[][] uv=new double[8][3];
    for(int i=0;i<8;i++){uv[i][0]=((i&1)==0?0:1)*(layout==0?1:layout==1?2.5:.125)+(layout==1?-.25:0);uv[i][v]=((i&(1<<v))==0?0:1)*(layout==0?1:layout==1?1.75:.5)+(layout==2?.5:0);uv[i][first==0?1:2]=(i&(first==0?2:4))==0?0:1;if(inverted){uv[i][0]=1-uv[i][0];uv[i][v]=1-uv[i][v];}}
    var upper=texture(32,64);var lower=texture(128,16);NativeTrapdoorMesh mesh=variant<3?NativeTrapdoorMesh.surface(TrapdoorSurfaceLayout.build(uv,first,first+2,layout==1,variant>0,variant==2),upper,lower):NativeTrapdoorMesh.frame(TrapdoorFrameLayout.build(uv,first,layout==1,first==0?3/16D:2/16D,variant==3?0:5,borders,aspect),upper,lower);
    var expected=row.getAsJsonArray("vertices");int[] vertex={-1},calls={0};VertexConsumer target=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,values)->{
     if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,values);int at=vertex[0]*8;
     switch(method.getName()){
      case "addVertex":at=++vertex[0]*8;for(int a=0;a<3;a++)need((Float)values[a]==Float.intBitsToFloat(expected.get(at+a).getAsInt()),"position");break;
      case "setColor":for(Object value:values)need((Integer)value==255,"RGBA");break;
      case "setUv":for(int a=0;a<2;a++)need(Float.floatToRawIntBits((Float)values[a])==expected.get(at+4+a).getAsInt(),"UV");break;
      case "setLight":need((Integer)values[0]==expected.get(at+6).getAsInt(),"light");break;
      case "setOverlay":need((Integer)values[0]==123,"overlay");break;
      case "setNormal":int normal=0;for(int a=0;a<3;a++)normal|=(((byte)((Float)values[a]*127))&255)<<(a*8);need(normal==(expected.get(at+7).getAsInt()&0xffffff),"normal");break;
     }calls[0]++;return method.getReturnType()==VertexConsumer.class?proxy:null;
    });mesh.emit(corners,new double[8][3],new PoseStack().last(),target,0xD00070,123);need((vertex[0]+1)*8==expected.size()&&calls[0]==(vertex[0]+1)*6,"complete active records");profiles++;vertices+=vertex[0]+1;
   }
  }need(profiles==4800&&vertices==588800,"oracle coverage");System.out.println("[vandorlabs][trapdoor-mesh] CPU PASS profiles="+profiles+" vertices="+vertices+" actual source positions/UV/quantized normals/light plus native overlay/RGBA/default consumer dispatch; GPU pending");
 }
 private static SegmentedPanelMesh.Texture texture(int x,int y){float u0=x/256F,u1=(x+16)/256F,v0=y/256F,v1=(y+32)/256F;return new SegmentedPanelMesh.Texture(){public float u(double pixel){return u0+(u1-u0)*(float)pixel/16F;}public float v(double pixel){return v0+(v1-v0)*(float)pixel/16F;}};}
 private static void need(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
