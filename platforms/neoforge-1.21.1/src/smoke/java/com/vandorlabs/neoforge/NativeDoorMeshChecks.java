package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.render.DoorMeshTransform;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.Direction;

/** CPU native consumer contract; Forge freezes the shared transform separately. */
public final class NativeDoorMeshChecks {
 public static void main(String[] args)throws Exception{
  int[] source=new int[32];float[][] points={{-.25F,0,.75F},{1.25F,0,.875F},{1.25F,2,.875F},{-.25F,2,.75F}};
  for(int v=0;v<4;v++){for(int a=0;a<3;a++)source[v*8+a]=Float.floatToRawIntBits(points[v][a]);source[v*8+3]=0xff7193ab;source[v*8+4]=Float.floatToRawIntBits(.25F);source[v*8+5]=Float.floatToRawIntBits(.75F);source[v*8+6]=0x11223344;source[v*8+7]=0x007f00;}
  int[] frozen=source.clone();Constructor<NativeDoorMesh> ctor=NativeDoorMesh.class.getDeclaredConstructor(int[].class,boolean[].class,boolean.class);ctor.setAccessible(true);int cases=0;
  for(boolean panel:new boolean[]{false,true})for(Direction face:Direction.Plane.HORIZONTAL)for(double size:new double[]{1,1.5})for(double leaf:new double[]{0,1.5})for(double angle:new double[]{-90,-45,0,45,90})for(double slide:new double[]{-1.5,0,1.5}){
   NativeDoorMesh mesh=ctor.newInstance(source,new boolean[]{false},panel);int[] expected=source.clone();DoorMeshTransform.write(source,8,expected,panel,false,face.get3DDataValue(),0,0,0,.125,0xc00050,slide,slide*2,.09375*size,.7025,angle,size,leaf);
   int[] vertex={-1},calls={0};VertexConsumer consumer=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,values)->{
    switch(method.getName()){
     case "addVertex":int i=++vertex[0];for(int axis=0;axis<3;axis++)if(Float.floatToIntBits((Float)values[axis])!=expected[i*8+axis])throw new AssertionError("Native mesh position");break;
     case "setColor":if((Integer)values[0]!=source[3])throw new AssertionError("Source ABGR color");break;
     case "setUv":if((Float)values[0]!=.25F||(Float)values[1]!=.75F)throw new AssertionError("Source UV payload");break;
     case "setLight":if((Integer)values[0]!=0xc00050)throw new AssertionError("Native light convention");break;
     case "setOverlay":if((Integer)values[0]!=123)throw new AssertionError("Overlay");break;
     case "setNormal":if(Math.abs((Float)values[0])>1E-6||Math.abs((Float)values[1]-1)>1E-6||Math.abs((Float)values[2])>1E-6)throw new AssertionError("Native transformed normal");break;
    }calls[0]++;return method.getReturnType()==VertexConsumer.class?proxy:null;
   });
   mesh.emit(new PoseStack().last(),consumer,face,.125,0xc00050,123,slide,slide*2,.09375*size,.7025,angle,size,leaf,1,false);
   if(vertex[0]!=3||calls[0]!=24||!Arrays.equals(source,frozen))throw new AssertionError("Native emission must retain immutable source records");
   mesh.emit(new PoseStack().last(),consumer,face,.125,0xc00050,123,slide,slide*2,.09375*size,.7025,angle,size,leaf,1,true);if(vertex[0]!=3)throw new AssertionError("Diffuse groups must remain separate");cases++;
  }
  System.out.println("[vandorlabs][door-mesh] CPU PASS poses="+cases+" native positions/colors/UV/light/overlay/normals/source immutability/diffuse groups; GPU integration pending");
 }
}
