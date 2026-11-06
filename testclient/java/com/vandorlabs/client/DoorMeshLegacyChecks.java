package com.vandorlabs.client;
import com.vandorlabs.render.DoorMeshTransform;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
/** Exact source CPU batch output, independent of GL and the native renderer. */
public final class DoorMeshLegacyChecks {
 public static void main(String[] args)throws Exception{
  int[] source=new int[28];float[][] p={{-.25F,0,.75F},{1.25F,0,.875F},{1.25F,2,.875F},{-.25F,2,.75F}};
  for(int v=0;v<4;v++){for(int a=0;a<3;a++)source[v*7+a]=Float.floatToRawIntBits(p[v][a]);source[v*7+3]=0xff7193ab;source[v*7+4]=Float.floatToRawIntBits(.25F);source[v*7+5]=Float.floatToRawIntBits(.75F);source[v*7+6]=0x11223344;}
  java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");int checks=0;
  for(boolean panel:new boolean[]{false,true})for(EnumFacing face:EnumFacing.HORIZONTALS)for(double size:new double[]{1,1.5})for(double leaf:new double[]{0,1.5})for(double angle:new double[]{-90,-45,0,45,90})for(double slide:new double[]{-1.5,0,1.5}){
   OpaqueDoorBatch.Mesh original=new OpaqueDoorBatch.Mesh(source,panel);BufferBuilder buffer=new BufferBuilder(256);buffer.begin(7,DefaultVertexFormats.BLOCK);original.draw(buffer,face,2,3,4,.125,0x00c00050,slide,slide*2,.09375*size,.7025,angle,size,leaf);buffer.finishDrawing();
   int[] actual=source.clone();DoorMeshTransform.write(source,7,actual,panel,true,face.getIndex(),2,3,4,.125,0x00c00050,slide,slide*2,.09375*size,.7025,angle,size,leaf);
   for(int i=0;i<actual.length;i++){int expected=buffer.getByteBuffer().getInt(i*4);if(actual[i]!=expected)throw new AssertionError("Door transform mismatch record="+checks+" field="+i);hash.update((byte)(expected>>>24));hash.update((byte)(expected>>>16));hash.update((byte)(expected>>>8));hash.update((byte)expected);}checks++;buffer.reset();
  }
  StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format("%02x",b&255));if(!digest.toString().equals("ce2d7ba5beef5e28a3c2bcece573fb19b9db825d37de80c7fb99245b57b95820"))throw new AssertionError("Released mesh output changed "+digest);System.out.println("Door mesh exact Forge CPU PASS poses="+checks+" SHA256="+digest);
 }
}
