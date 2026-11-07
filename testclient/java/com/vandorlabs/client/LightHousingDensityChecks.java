package com.vandorlabs.client;

import com.vandorlabs.light.ProgrammableLightMesh;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Mixed real housing emissions through the CPU Tessellator, without disposable worlds/corpora. */
public final class LightHousingDensityChecks {
 private static final class Profile {final double[] bounds;final TextureAtlasSprite sprite;final int visible,layout;final int[] expected;Profile(int i,BufferBuilder buffer){bounds=ProgrammableLightMesh.localBounds(i%3,(i/3)%6,(i&1)!=0,(i&2)!=0);sprite=new TextureAtlasSprite("density/light/"+i){};sprite.setIconWidth(16);sprite.setIconHeight(new int[]{8,16,32}[i%3]);sprite.initSprite(256,256,32,64,false);visible=63^(1<<(i%6));layout=i%3;buffer.begin(7,BlockSurfaceFormat.get());ProgrammableLightMesh.housing(bounds,visible,layout,UnifiedTextureSprites.aspect(sprite)).emit((x,y,z,u,v,nx,ny,nz)->WorldSurface.vertex(buffer,x,y,z,sprite.getInterpolatedU(u),sprite.getInterpolatedV(v),nx,ny,nz));buffer.finishDrawing();expected=buffer.getVertexState().getRawBuffer();}}
 private static void draw(Profile p){double[] b=p.bounds;LightHousingMeshes.render(p.sprite,p.visible,b[0],b[1],b[2],b[3],b[4],b[5],p.layout);}
 private static void same(Profile p,BufferBuilder buffer){if(buffer.getVertexCount()*BlockSurfaceFormat.get().getIntegerSize()!=p.expected.length)throw new AssertionError("Light density vertex count");for(int i=0;i<p.expected.length;i++)if(buffer.getByteBuffer().getInt(i*4)!=p.expected[i])throw new AssertionError("Light density emitted payload "+i);}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();OpenGlHelper.lastBrightnessX=0x70;OpenGlHelper.lastBrightnessY=0xD0;BufferBuilder buffer=new BufferBuilder(1<<16);
  try(PanelBoxEmitterChecks.TessellatorOverride cpu=new PanelBoxEmitterChecks.TessellatorOverride(buffer)){
   Profile[] profiles=new Profile[64];for(int i=0;i<profiles.length;i++)profiles[i]=new Profile(i,buffer);
   for(Profile profile:profiles){draw(profile);same(profile,buffer);}for(int i=0;i<20000;i++)draw(profiles[i&63]);
   com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long start=meter.getThreadAllocatedBytes(Thread.currentThread().getId()),vertices=0;for(int i=0;i<4096;i++){draw(profiles[i&63]);vertices+=buffer.getVertexCount();}long bytes=meter.getThreadAllocatedBytes(Thread.currentThread().getId())-start;
   for(Profile profile:profiles){draw(profile);same(profile,buffer);}System.out.println("Light mixed housing density calls=4096 profiles=64 vertices="+vertices+" allocatedBytes="+bytes+" exactPayloads=128");if(bytes!=0)throw new AssertionError("Mixed warmed housing lookup must allocate zero bytes, got "+bytes);
   TextureAtlasSprite pressure=new TextureAtlasSprite("density/pressure"){};pressure.setIconWidth(16);
   for(int height=16;height<528;height++){pressure.setIconHeight(height);pressure.initSprite(512,512,32,64,false);for(Profile p:profiles){double[] b=p.bounds;LightHousingMeshes.render(pressure,p.visible,b[0],b[1],b[2],b[3],b[4],b[5],p.layout);if(LightHousingMeshes.retainedBytes()>4*1024*1024)throw new AssertionError("Light retained byte bound");}}
   int retained=LightHousingMeshes.retainedBytes();if(retained<3*1024*1024)throw new AssertionError("Light pressure did not approach retained budget");for(Profile profile:profiles){draw(profile);same(profile,buffer);}LightHousingMeshes.clear();if(LightHousingMeshes.retainedBytes()!=256)throw new AssertionError("Light explicit retirement");for(Profile profile:profiles){draw(profile);same(profile,buffer);}System.out.println("Light density pressure PASS attempted=32768 retainedBytes="+retained+" exactPayloadsAfterPressureAndClear=128");
   System.out.println("Light mixed housing density PASS actual full/frame/slab bounds/allfacings/masks/layouts/aspects exact emitted records; CPU only");
  }finally{LightHousingMeshes.clear();}
 }
}
