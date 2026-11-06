package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.segmented.SegmentedDoorSettings;
import com.vandorlabs.tiles.DoorGlassAppearance;
import java.io.*;
import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.*;
/** Actual private panel builder with explicit world-free descriptor and deterministic atlas. */
public final class SegmentedPanelLegacyChecks {
 private static final class Sprite extends TextureAtlasSprite {Sprite(){super("vandorlabs:oracle/segmented");setIconWidth(16);setIconHeight(16);initSprite(256,256,32,64,false);}}
 private static void set(Object target,String name,Object value)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field.get(null);Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);for(Field f:Minecraft.class.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==Minecraft.class){f.setAccessible(true);f.set(null,mc);}final Sprite sprite=new Sprite();TextureMap atlas=new TextureMap(""){@Override public TextureAtlasSprite getAtlasSprite(String name){return sprite;}};for(Field f:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&f.getType()==TextureMap.class){f.setAccessible(true);f.set(mc,atlas);}
  if(args.length>1){header(args[0]);return;}
  Class<?> keyClass=Class.forName("com.vandorlabs.client.SegmentedDoorPanels$Key");Method build=SegmentedDoorPanels.class.getDeclaredMethod("build",keyClass);build.setAccessible(true);File target=new File(args[0]);target.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,vertices=0;
  try(PrintWriter out=new PrintWriter(target,"UTF-8")){
   JsonObject header=new JsonObject();header.addProperty("u0",sprite.getMinU());header.addProperty("u1",sprite.getMaxU());header.addProperty("v0",sprite.getMinV());header.addProperty("v1",sprite.getMaxV());out.println(gson.toJson(header));
   for(boolean window:new boolean[]{false,true})for(int shape=0;shape<(window?4:1);shape++)for(int layout=0;layout<3;layout++)for(int size:new int[]{1,3})for(int row=0;row<size;row++)for(int column=0;column<size;column++)for(int pass=0;pass<2;pass++)for(int tint:new int[]{0,1,3}){
    Object key=unsafe.allocateInstance(keyClass);set(key,"settings",new SegmentedDoorSettings(0,false,layout,0,window,shape,size>1,new DoorGlassAppearance(-1,0,tint,layout)));String[] names={"texture","column","row","width","height","pass","wc","wr","ww","wh"};int[] values={-1,column,row,size,size,pass,column,row,size,size};for(int n=0;n<names.length;n++)set(key,names[n],values[n]);StaticSurfaceMesh mesh=(StaticSurfaceMesh)build.invoke(null,key);BufferBuilder b=new BufferBuilder(4096);b.begin(7,BlockSurfaceFormat.get());mesh.draw(b,0,0);b.finishDrawing();JsonObject record=new JsonObject();record.addProperty("window",window);record.addProperty("shape",shape);record.addProperty("layout",layout);record.addProperty("size",size);record.addProperty("column",column);record.addProperty("row",row);record.addProperty("pass",pass);record.addProperty("tint",tint);JsonArray data=new JsonArray();for(int v=0;v<b.getVertexCount();v++)for(int a=0;a<8;a++)data.add(b.getByteBuffer().getInt(v*32+a*4));record.add("vertices",data);out.println(gson.toJson(record));profiles++;vertices+=b.getVertexCount();
   }
  }System.out.println("Segmented panel actual Forge PASS profiles="+profiles+" vertices="+vertices+" window slices/TileFit UV/frontback/reveal edges/opaque pane policy");
 }
 private static void header(String path)throws Exception{
  Method header=com.vandorlabs.client.segmented.SegmentedDoorRenderer.class.getDeclaredMethod("header",com.vandorlabs.segmented.SegmentedDoorTile.class,double.class,double.class,double.class,BufferBuilder.class);header.setAccessible(true);Object renderer=new com.vandorlabs.client.segmented.SegmentedDoorRenderer();final net.minecraft.block.state.IBlockState[] state={new com.vandorlabs.segmented.SegmentedDoorBlock().getDefaultState()};NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false){@Override public net.minecraft.block.state.IBlockState getBlockState(net.minecraft.util.math.BlockPos p){return state[0];}@Override public int getCombinedLight(net.minecraft.util.math.BlockPos p,int min){return 0x00c00070;}};File target=new File(path);target.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,vertices=0;
  try(PrintWriter out=new PrintWriter(target,"UTF-8")){for(final net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.HORIZONTALS)for(int direction=0;direction<4;direction++)for(int position=0;position<3;position++)for(int height:new int[]{1,3,17})for(int column=0;column<3;column++){
   com.vandorlabs.segmented.SegmentedDoorTile tile=new com.vandorlabs.segmented.SegmentedDoorTile();state[0]=state[0].withProperty(com.vandorlabs.segmented.SegmentedDoorBlock.FACING,face);tile.setWorld(world);tile.height=height;tile.column=column;tile.columns=3;tile.settings=new SegmentedDoorSettings(direction,false,1,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass,position);BufferBuilder b=new BufferBuilder(4096);b.begin(7,BlockSurfaceFormat.get());header.invoke(renderer,tile,0D,0D,0D,b);b.finishDrawing();JsonObject record=new JsonObject();record.addProperty("rotation",((int)(180-face.getHorizontalAngle())/90)&3);record.addProperty("direction",direction);record.addProperty("position",position);record.addProperty("height",height);record.addProperty("column",column);JsonArray data=new JsonArray();for(int v=0;v<b.getVertexCount();v++)for(int a=0;a<8;a++)data.add(b.getByteBuffer().getInt(v*32+a*4));record.add("vertices",data);out.println(gson.toJson(record));profiles++;vertices+=b.getVertexCount();
  }}System.out.println("Segmented header actual Forge PASS profiles="+profiles+" vertices="+vertices+" fixed border placement/metric tile cropping/facings");
 }

}
