package com.vandorlabs.client;

import com.vandorlabs.render.ReaderVisualMesh;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;

/** Actual TETokenSlot private emission and cached bases, without a GL context. */
public final class ReaderVisualLegacyChecks {
 static final class Point {double x,y,z;int color;Point(double x,double y,double z,int color){this.x=x;this.y=y;this.z=z;this.color=color;}}
 public static void main(String[] args)throws Exception{
  Class<?> basisClass=Class.forName("com.vandorlabs.client.TETokenSlot$FaceBasis");Field basesField=TETokenSlot.class.getDeclaredField("BASES");basesField.setAccessible(true);Object[][] bases=(Object[][])basesField.get(null);
  Method panel=TETokenSlot.class.getDeclaredMethod("panel",BufferBuilder.class,basisClass,double.class,double.class,double.class,double.class,double.class,int.class);panel.setAccessible(true);
  MessageDigest hash=MessageDigest.getInstance("SHA-256");int variants=0,vertices=0;
  for(int face=0;face<6;face++)for(int rotation=0;rotation<4;rotation++)for(boolean chip:new boolean[]{false,true})for(boolean rejected:new boolean[]{false,true})for(boolean on:new boolean[]{false,true}){
   Object basis=bases[face][rotation];BufferBuilder buffer=new BufferBuilder(2048);buffer.begin(7,DefaultVertexFormats.POSITION_COLOR);double h=(chip?3:4.4)/16;
   if(rejected)for(int pixel=-2;pixel<=2;pixel++){panel.invoke(null,buffer,basis,(pixel-.35)/16,(pixel+.35)/16,(pixel-.35)/16,(pixel+.35)/16,.026,0xffb340);if(pixel!=0)panel.invoke(null,buffer,basis,(pixel-.35)/16,(pixel+.35)/16,(-pixel-.35)/16,(-pixel+.35)/16,.026,0xffb340);}
   panel.invoke(null,buffer,basis,.9/16,2.1/16,-h+.22/16,-h+.47/16,.098,on?0x5cffff:0x1c6876);panel.invoke(null,buffer,basis,-2.1/16,-.9/16,-h+.22/16,-h+.47/16,.098,0xc68a37);buffer.finishDrawing();
   List<Point> actual=new ArrayList<>();ReaderVisualMesh.overlays(ReaderVisualMesh.basis(face,rotation),chip,rejected,on,(x,y,z,u,v,rgb)->actual.add(new Point(x,y,z,rgb)));
   if(actual.size()!=buffer.getVertexCount())throw new AssertionError("Reader overlay vertex count");
   for(int i=0;i<actual.size();i++){Point p=actual.get(i);double[] xyz={p.x,p.y,p.z};for(int a=0;a<3;a++)if(Float.floatToRawIntBits((float)xyz[a])!=buffer.getByteBuffer().getInt(i*16+a*4))throw new AssertionError("Reader overlay source position face="+face+" rotation="+rotation);for(int c=0;c<3;c++)if(((p.color>>>(16-c*8))&255)!=(buffer.getByteBuffer().get(i*16+12+c)&255))throw new AssertionError("Reader source RGB");for(int j=0;j<16;j++)hash.update(buffer.getByteBuffer().get(i*16+j));vertices++;}variants++;
  }
  Method logo=TETokenSlot.class.getDeclaredMethod("logoVertex",BufferBuilder.class,basisClass,double.class,double.class,float.class,float.class);logo.setAccessible(true);Field itemMatrix=basisClass.getDeclaredField("itemMatrix");itemMatrix.setAccessible(true);int logos=0;
  for(int face=0;face<6;face++)for(int rotation=0;rotation<4;rotation++){
   Object basis=bases[face][rotation];ReaderVisualMesh.Basis b=ReaderVisualMesh.basis(face,rotation);java.nio.FloatBuffer matrix=(java.nio.FloatBuffer)itemMatrix.get(basis);float[] expected={(float)-b.ux,(float)-b.uy,(float)-b.uz,0,(float)b.vx,(float)b.vy,(float)b.vz,0,(float)b.nx,(float)b.ny,(float)b.nz,0,(float)(b.ox+b.nx*.06),(float)(b.oy+b.ny*.06),(float)(b.oz+b.nz*.06),1};for(int at=0;at<16;at++)if(Float.floatToRawIntBits(matrix.get(at))!=Float.floatToRawIntBits(expected[at]))throw new AssertionError("Reader source item matrix face="+face+" rotation="+rotation+" field="+at);
   BufferBuilder buffer=new BufferBuilder(256);buffer.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR);double h=2.2/16;logo.invoke(null,buffer,basis,-h,-h,.75F,.625F);logo.invoke(null,buffer,basis,h,-h,.25F,.625F);logo.invoke(null,buffer,basis,h,h,.25F,.125F);logo.invoke(null,buffer,basis,-h,h,.75F,.125F);buffer.finishDrawing();
   final int[] vertex={0};ReaderVisualMesh.logo(b,.25F,.75F,.125F,.625F,(x,y,z,u,v,color)->{float[] values={(float)x,(float)y,(float)z,u,v};for(int a=0;a<5;a++)if(Float.floatToRawIntBits(values[a])!=buffer.getByteBuffer().getInt(vertex[0]*24+a*4))throw new AssertionError("Reader source logo vertices/UV");vertex[0]++;});if(vertex[0]!=4)throw new AssertionError("Reader logo count");logos++;
  }
  StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format("%02x",b&255));if(!digest.toString().equals("02306ee9d051a0026d92155a98c87a490b9eeaebdaddb31d7c47828348abc589"))throw new AssertionError("Released reader overlays changed "+digest);System.out.println("Reader visual actual Forge PASS overlayVariants="+variants+" logoAndItemBases="+logos+" vertices="+vertices+" SHA256="+digest);
 }
}
