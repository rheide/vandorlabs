package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.render.*;
import java.io.*;
import java.lang.reflect.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Released BufferBuilder primitives used by the seven screen roles; no replacement emitter participates. */
public final class ScreenEmissionLegacyOracle {
 private static final Gson JSON=new Gson();
 private static final double[] BORDER={.08,.13,.06,.11};
 private static int profiles;private static long vertices;
 private static Method housing,surface;private static boolean shared;
 private static ScreenMeshEmission.Sink sink(BufferBuilder b,TextureAtlasSprite sprite){return (x,y,z,u,v,nx,ny,nz)->WorldSurface.vertex(b,x,y,z,sprite==null?u:sprite.getInterpolatedU(u),sprite==null?v:sprite.getInterpolatedV(v),nx,ny,nz);}
 private static TextureAtlasSprite sprite(int height){TextureAtlasSprite s=new TextureAtlasSprite("oracle/screen"){};s.setIconWidth(16);s.setIconHeight(height);s.initSprite(256,256,32,64,false);return s;}
 private static void write(PrintWriter out,String role,String part,int variant,int layout,int height,boolean ceiling,BufferBuilder b){
  b.finishDrawing();JsonObject row=new JsonObject();row.addProperty("role",role);row.addProperty("part",part);row.addProperty("variant",variant);row.addProperty("layout",layout);row.addProperty("height",height);row.addProperty("ceiling",ceiling);JsonArray data=new JsonArray();for(int i=0;i<b.getVertexCount()*BlockSurfaceFormat.get().getNextOffset();i+=4)data.add(b.getByteBuffer().getInt(i));row.add("vertices",data);out.println(JSON.toJson(row));profiles++;vertices+=b.getVertexCount();
 }
 private static void mesh(PrintWriter out,BufferBuilder b,String role,int variant,int layout,int height,boolean ceiling,ScreenHousingMesh mesh,boolean applyLayout)throws Exception {
  TextureAtlasSprite s=sprite(height);if(applyLayout)mesh=mesh.textureLayout(layout,UnifiedTextureSprites.aspect(s));
  b.begin(7,BlockSurfaceFormat.get());if(shared)ScreenMeshEmission.housing(mesh.quads,ceiling,sink(b,s));else housing.invoke(null,b,s,mesh.quads,ceiling);write(out,role,"housing-quads",variant,layout,height,ceiling,b);
  if(mesh.triangles.length>0){b.begin(4,BlockSurfaceFormat.get());if(shared)ScreenMeshEmission.housing(mesh.triangles,ceiling,sink(b,s));else housing.invoke(null,b,s,mesh.triangles,ceiling);write(out,role,"housing-triangles",variant,layout,height,ceiling,b);}
 }
 private static void face(PrintWriter out,BufferBuilder b,String role,int variant,boolean ceiling,ScreenSurface.Quad quad,boolean input)throws Exception {
  for(int mode=0;mode<3;mode++)for(int frame=0;frame<3;frame++){
   ScreenSurface.Quad content=frame==0?quad:ScreenSurface.inset(quad,BORDER[0],BORDER[1],BORDER[2],BORDER[3]);
   if(frame==2)content=ScreenSurface.half(content,true);
   double top=mode==2?2/7D:0,bottom=mode==2?3/7D:1,left=mode==1?.125:0,right=mode==1?.875:1;
   b.begin(7,BlockSurfaceFormat.get());if(shared)ScreenMeshEmission.surface(content,input?right:left,input?left:right,top,bottom,ceiling,sink(b,null));else surface.invoke(null,b,content,input?right:left,input?left:right,top,bottom,ceiling);write(out,role,"surface-mode"+mode+"-frame"+frame,variant,0,0,ceiling,b);
  }
  for(int aspect=0;aspect<3;aspect++){
   TextureAtlasSprite s=sprite(new int[]{8,16,32}[aspect]);b.begin(7,BlockSurfaceFormat.get());
   for(int row=0;row<3;row++)for(int col=0;col<3;col++)if(row!=1||col!=1){double x0=ScreenFrameBorders.cut(col,BORDER[0],BORDER[1]),x1=ScreenFrameBorders.cut(col+1,BORDER[0],BORDER[1]),y0=ScreenFrameBorders.cut(row,BORDER[2],BORDER[3]),y1=ScreenFrameBorders.cut(row+1,BORDER[2],BORDER[3]);if(shared)ScreenMeshEmission.frameRegion(quad,input,ceiling,1D/(1<<aspect),x0,y0,x1,y1,x0,y0,x1,y1,sink(b,s));else FrameSurfaceRenderer.region(b,quad,s,input,ceiling,1D/(1<<aspect),x0,y0,x1,y1,x0,y0,x1,y1);}
   write(out,role,"frame-regions",variant,0,aspect,ceiling,b);
  }
 }
 private static void allocationProbe(){
  BufferBuilder b=new BufferBuilder(65536);ScreenMeshEmission.Sink sink=sink(b,sprite(32));ScreenHousingMesh mesh=ScreenHousingMesh.console();ScreenSurface.Quad quad=ScreenSurface.quad(ScreenSurface.Kind.CONSOLE,false);
  for(int i=0;i<12000;i++){b.begin(7,BlockSurfaceFormat.get());ScreenMeshEmission.housing(mesh.quads,false,sink);ScreenMeshEmission.surface(quad,.1,.9,.2,.7,false,sink);ScreenMeshEmission.frameRegion(quad,false,false,.5,.08,.06,.87,.89,0,0,1,1,sink);b.finishDrawing();}
  com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId(),before=meter.getThreadAllocatedBytes(thread),count=0;
  for(int i=0;i<4096;i++){b.begin(7,BlockSurfaceFormat.get());ScreenMeshEmission.housing(mesh.quads,false,sink);ScreenMeshEmission.surface(quad,.1,.9,.2,.7,false,sink);ScreenMeshEmission.frameRegion(quad,false,false,.5,.08,.06,.87,.89,0,0,1,1,sink);b.finishDrawing();count+=b.getVertexCount();}
  long bytes=meter.getThreadAllocatedBytes(thread)-before;if(bytes!=0)throw new AssertionError("Screen warmed primitive emission allocated "+bytes);System.out.println("Screen shared emission allocation PASS calls=4096 vertices="+count+" bytes="+bytes);
 }
 public static void main(String[] args)throws Exception {
  shared=args.length>1&&args[1].equals("shared");net.minecraft.init.Bootstrap.register();housing=TEAnimatedScreenSelector.class.getDeclaredMethod("drawWallFaces",BufferBuilder.class,TextureAtlasSprite.class,ScreenHousingMesh.Face[].class,boolean.class);housing.setAccessible(true);surface=TEAnimatedScreenSelector.class.getDeclaredMethod("drawScreenQuad",BufferBuilder.class,ScreenSurface.Quad.class,double.class,double.class,double.class,double.class,boolean.class);surface.setAccessible(true);
  net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX=0x70;net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY=0xD0;
  File target=new File(args[0]);target.getAbsoluteFile().getParentFile().mkdirs();BufferBuilder b=new BufferBuilder(65536);
  try(PrintWriter out=new PrintWriter(target,"UTF-8")){
   // Source role assembly is kept explicit: the diagonal housing deliberately does not apply the layout setting.
   for(int layout=0;layout<3;layout++)for(int height:new int[]{8,16,32}){
    mesh(out,b,"SCREEN",0,layout,height,false,ScreenHousingMesh.box(0,0,0,16,16,16),true);
    for(String role:new String[]{"CONSOLE","HALF_CONSOLE"}){mesh(out,b,role,0,layout,height,false,ScreenHousingMesh.box(0,0,0,16,1,16),true);mesh(out,b,role,1,layout,height,false,role.equals("CONSOLE")?ScreenHousingMesh.console():ScreenHousingMesh.halfConsole(),true);}
    for(boolean inverted:new boolean[]{false,true})mesh(out,b,"DIAGONAL",inverted?1:0,layout,height,false,ScreenHousingMesh.diagonal(inverted),false);
    for(boolean ceiling:new boolean[]{false,true})mesh(out,b,"HALF_DIAGONAL",0,layout,height,ceiling,ScreenHousingMesh.diagonal(false),true);
    for(boolean full:new boolean[]{false,true})for(boolean small:new boolean[]{false,true})for(boolean keyboard:new boolean[]{false,true})for(boolean upper:new boolean[]{false,true})for(int position=0;position<3;position++){
     InputSurfaceLayout.Mounted m=full?InputSurfaceLayout.fullInput(keyboard,upper,small):InputSurfaceLayout.halfInput(keyboard,upper,position,small);int variant=(full?32:0)+(small?16:0)+(keyboard?8:0)+(upper?4:0)+position;
     mesh(out,b,full?"INPUT":"HALF_INPUT",variant,layout,height,false,ScreenHousingMesh.box(m.housing.x0,m.housing.y0,m.housing.z0,m.housing.x1,m.housing.y1,m.housing.z1),true);
     if(layout==0&&height==16)face(out,b,full?"INPUT":"HALF_INPUT",variant,false,ScreenSurface.input(m.surface),true);
    }
    for(boolean full:new boolean[]{false,true})for(boolean small:new boolean[]{false,true})for(int position=0;position<3;position++){
     InputSurfaceLayout.Mounted m=InputSurfaceLayout.ceilingInput(full,small,position);int variant=64+(full?32:0)+(small?16:0)+position;
     mesh(out,b,full?"INPUT":"HALF_INPUT",variant,layout,height,false,ScreenHousingMesh.box(m.housing.x0,m.housing.y0,m.housing.z0,m.housing.x1,m.housing.y1,m.housing.z1),true);
     if(layout==0&&height==16)face(out,b,full?"INPUT":"HALF_INPUT",variant,false,ScreenSurface.input(m.surface),true);
    }
   }
   face(out,b,"SCREEN",0,false,ScreenSurface.quad(ScreenSurface.Kind.FLAT,false),false);face(out,b,"CONSOLE",0,false,ScreenSurface.quad(ScreenSurface.Kind.CONSOLE,false),false);
   for(String role:new String[]{"CONSOLE","HALF_CONSOLE"}){face(out,b,role,1,false,ScreenSurface.input(InputSurfaceLayout.halfConsoleFront()),true);if(role.equals("HALF_CONSOLE"))face(out,b,role,2,false,ScreenSurface.input(InputSurfaceLayout.halfConsoleRear()),true);}
   for(boolean inverted:new boolean[]{false,true})face(out,b,"DIAGONAL",inverted?1:0,false,ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,inverted),false);
   for(boolean ceiling:new boolean[]{false,true})face(out,b,"HALF_DIAGONAL",0,ceiling,ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,false),false);
  }
  if(target.length()>16*1024*1024)throw new AssertionError("Screen emission corpus exceeded byte budget: "+target.length());
  SegmentedReferenceGolden.check(target,"79ba1321a4a5767fe5d8a5cb9f84684ac5d5bdac101388eb8e808e150d008c06");
  if(shared)allocationProbe();
  System.out.println("Actual Screen BufferBuilder primitives PASS profiles="+profiles+" vertices="+vertices+" seven roles, housing quads/triangles, three playback UVs, inset/split/frame regions; role GL transforms/binding remain separate");
 }
}
