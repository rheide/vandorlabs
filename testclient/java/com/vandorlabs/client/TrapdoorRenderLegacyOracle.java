package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.render.*;
import com.vandorlabs.tiles.ScreenHousingTextures;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Frozen released surface/frame emitter bytes; no replacement renderer participates. */
public final class TrapdoorRenderLegacyOracle {
 public static void main(String[] args)throws Exception {
  net.minecraft.init.Bootstrap.register();
  int choice=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1");
  if(choice<0)throw new AssertionError("missing authored frame");
  Field cache=ScreenFrameBorders.class.getDeclaredField("CACHE");cache.setAccessible(true);
  double[] borders={.08,.12,.06,.09};((Map<Integer,double[]>)cache.get(null)).put(choice,borders);
  TextureAtlasSprite upper=sprite("upper",32,64),lower=sprite("lower",128,16);
  File target=new File(args[0]);target.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;long vertices=0;
  try(PrintWriter out=new PrintWriter(target,"UTF-8")) {
   JsonObject header=new JsonObject();header.add("borders",gson.toJsonTree(borders));header.addProperty("frameAspect",com.vandorlabs.tiles.FrameAppearance.aspect(choice));header.addProperty("stride",BlockSurfaceFormat.get().getNextOffset());out.println(gson.toJson(header));
   for(int shape=0;shape<4;shape++)for(int turn=0;turn<4;turn++)for(boolean inverted:new boolean[]{false,true})for(boolean sliding:new boolean[]{false,true})for(double pose:new double[]{0,.17,.5,.91,1})for(int layout=0;layout<3;layout++)for(int variant=0;variant<5;variant++) {
    double[][] corners=shape==0?TrapdoorGeometry.corners(1,sliding,turn,pose):DiagonalTrapdoorGeometry.corners(shape-1,inverted,turn,sliding,inverted,pose);
    int first=shape==1||shape==2?2:0,v=first==0?2:1;double[][] uv=new double[8][3];
    for(int i=0;i<8;i++){uv[i][0]=((i&1)==0?0:1)*(layout==0?1:layout==1?2.5:.125)+(layout==1?-.25:0);uv[i][v]=((i&(1<<v))==0?0:1)*(layout==0?1:layout==1?1.75:.5)+(layout==2?.5:0);uv[i][first==0?1:2]=(i&(first==0?2:4))==0?0:1;if(inverted){uv[i][0]=1-uv[i][0];uv[i][v]=1-uv[i][v];}}
    BufferBuilder b=new BufferBuilder(65536);b.begin(7,BlockSurfaceFormat.get());
    if(variant<3)TrapdoorSurfaceMesh.draw(b,upper,lower,corners,uv,0xD00070,first,first+2,layout==1,variant>0,variant==2);
    else TrapdoorFrameMesh.draw(b,upper,lower,corners,uv,new double[8][3],0xD00070,choice,first,layout==1,first==0?3/16D:2/16D,variant==3?0:5);
    b.finishDrawing();JsonObject record=new JsonObject();record.addProperty("shape",shape);record.addProperty("turn",turn);record.addProperty("inverted",inverted);record.addProperty("sliding",sliding);record.addProperty("pose",pose);record.addProperty("layout",layout);record.addProperty("variant",variant);JsonArray data=new JsonArray();for(int i=0;i<b.getVertexCount()*BlockSurfaceFormat.get().getNextOffset();i+=4)data.add(b.getByteBuffer().getInt(i));record.add("vertices",data);out.println(gson.toJson(record));profiles++;vertices+=b.getVertexCount();
   }
  }
  SegmentedReferenceGolden.check(target,"e83a807e9f890939ed2d9b326dc1b79176529426a2ae78b91a4cab4c1209143c");
  System.out.println("Trapdoor actual released mesh PASS profiles="+profiles+" vertices="+vertices+" surface/custom split/mirror/asymmetric frames/join masks");
 }
 private static TextureAtlasSprite sprite(String name,int x,int y){TextureAtlasSprite s=new TextureAtlasSprite("oracle/"+name){};s.setIconWidth(16);s.setIconHeight(32);s.initSprite(256,256,x,y,false);return s;}
}
