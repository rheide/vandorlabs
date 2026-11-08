package com.vandorlabs.client;

import com.vandorlabs.ramp.RampGeometry;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** Headless C2 compilation pressure on the exact production face emitter. */
public final class RampJitStressChecks {
 public static void main(String[] args){
  System.out.println("Ramp C2 runtime: "+System.getProperty("java.runtime.version")+" / "+System.getProperty("java.vm.version"));
  net.minecraft.init.Bootstrap.register();RampRenderChecks.run();
  TextureAtlasSprite sprite=new TextureAtlasSprite("ramp-jit-stress"){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,32,48,false);
  BufferBuilder buffer=new BufferBuilder(4096);TEControlledRamp.FaceEmitter emitter=new TEControlledRamp.FaceEmitter(buffer);EnumFacing[] faces=EnumFacing.values();
  RampGeometry.Box[] boxes={new RampGeometry.Box(.1,0,.2,.9,.001,.95),new RampGeometry.Box(0,-.5,0,1,1.5,1),new RampGeometry.Box(0,0,0,1,1,1)};
  long vertices=0,checksum=0;OpenGlHelper.lastBrightnessX=80;OpenGlHelper.lastBrightnessY=192;
  for(int iteration=0;iteration<60000;iteration++){
   int layout=iteration%4-1;double shift=iteration%3==0?0:iteration%3==1?-2.3:2.8;buffer.begin(7,BlockSurfaceFormat.get());
   emitter.emit(boxes[iteration%boxes.length],faces[iteration%faces.length],sprite,.17,.83,shift,.11,0x7BAF53,layout);buffer.finishDrawing();
   if(buffer.getVertexCount()==0||buffer.getVertexCount()%4!=0)throw new AssertionError("Lost JIT-compiled face");vertices+=buffer.getVertexCount();checksum+=buffer.getByteBuffer().getInt(0);
  }
  if(vertices!=860000L||checksum!=47362080770000L)throw new AssertionError("Changed ramp vertices: "+vertices+" / "+checksum);
  System.out.println("Ramp C2 stress PASS iterations=60000 vertices="+vertices+" checksum="+checksum);
 }
}
