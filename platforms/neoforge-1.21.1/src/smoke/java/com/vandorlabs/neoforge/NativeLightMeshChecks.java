package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.light.ProgrammableLightMesh;
import com.vandorlabs.segmented.SegmentedPanelMesh;
import java.lang.reflect.*;
import java.nio.file.*;
import net.minecraft.core.Direction;

/** Frozen released local records and side mappings through native active-consumer dispatch. */
public final class NativeLightMeshChecks {
 private static void need(boolean yes,String why){if(!yes)throw new AssertionError(why);}
 private static float component(Direction direction,int axis){return axis==0?direction.getStepX():axis==1?direction.getStepY():direction.getStepZ();}
 public static void main(String[] args)throws Exception {
  int profiles=0;long vertices=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();int family=row.get("family").getAsInt(),facing=row.get("facing").getAsInt(),height=row.get("height").getAsInt();double[] bounds=ProgrammableLightMesh.localBounds(family,facing,row.get("upper").getAsBoolean(),row.get("small").getAsBoolean());for(int i=0;i<6;i++)need(bounds[i]==row.getAsJsonArray("bounds").get(i).getAsDouble()*16,"source local bounds");ProgrammableLightMesh mesh;
   if(row.get("part").getAsString().equals("housing"))mesh=ProgrammableLightMesh.housing(bounds,row.get("visible").getAsInt(),row.get("layout").getAsInt(),16D/height);else{var uv=row.getAsJsonArray("uv");mesh=ProgrammableLightMesh.face(bounds,uv.get(0).getAsDouble(),uv.get(1).getAsDouble(),uv.get(2).getAsDouble(),uv.get(3).getAsDouble());}
   var expected=row.getAsJsonArray("vertices");var sides=row.getAsJsonArray("worldSides");var right=Direction.from3DDataValue(sides.get(5).getAsInt());var up=Direction.from3DDataValue(sides.get(1).getAsInt());var back=Direction.from3DDataValue(sides.get(3).getAsInt());int[] vertex={-1},calls={0};String label="record "+profiles;
   VertexConsumer target=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,values)->{if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,values);int at=vertex[0]*8;
    switch(method.getName()){
     case "addVertex"->{at=++vertex[0]*8;float x=Float.intBitsToFloat(expected.get(at).getAsInt())/16F-.5F,y=Float.intBitsToFloat(expected.get(at+1).getAsInt())/16F-.5F,z=Float.intBitsToFloat(expected.get(at+2).getAsInt())/16F-.5F;for(int a=0;a<3;a++)need((Float)values[a]==.5F+component(right,a)*x+component(up,a)*y+component(back,a)*z,label+" world position");}
     case "setUv"->{for(int a=0;a<2;a++)need(Float.floatToRawIntBits((Float)values[a])==expected.get(at+4+a).getAsInt(),label+" UV");}
     case "setColor"->{for(Object value:values)need((Integer)value==255,label+" white RGBA");}
     case "setOverlay"->need((Integer)values[0]==123,label+" overlay");
     case "setLight"->need((Integer)values[0]==expected.get(at+6).getAsInt(),label+" exact asymmetric light");
     case "setNormal"->{int packed=expected.get(at+7).getAsInt();float x=(byte)packed/127F,y=(byte)(packed>>>8)/127F,z=(byte)(packed>>>16)/127F;for(int a=0;a<3;a++)need((Float)values[a]==component(right,a)*x+component(up,a)*y+component(back,a)*z,label+" world normal");}
    }calls[0]++;return method.getReturnType()==VertexConsumer.class?proxy:null;
   });float u0=32/256F,u1=48/256F,v0=64/256F,v1=(64+height)/256F;var texture=new SegmentedPanelMesh.Texture(){public float u(double p){return u0+(u1-u0)*(float)p/16F;}public float v(double p){return v0+(v1-v0)*(float)p/16F;}};
   new NativeLightMesh(mesh,Direction.from3DDataValue(facing),texture).emit(new PoseStack().last(),target,0xD00070,123);need((vertex[0]+1)*8==expected.size()&&calls[0]==(vertex[0]+1)*6,label+" active format complete");profiles++;vertices+=vertex[0]+1;
  }}need(profiles==44064&&vertices==425088,"source corpus coverage");System.out.println("[vandorlabs][light-mesh] CPU PASS profiles="+profiles+" vertices="+vertices+" exact source UV/asymmetric light/source-local normals and six world rotations; active consumer format");
 }
}
