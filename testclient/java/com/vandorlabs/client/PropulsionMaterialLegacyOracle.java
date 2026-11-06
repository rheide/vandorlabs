package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockPropulsionLight;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;

/** Actual released housing wrapper, including ABGR tint and extended shader records. */
public final class PropulsionMaterialLegacyOracle {
 private static TextureAtlasSprite sprite(String id,int x,int y,int width,int height){TextureAtlasSprite s=new TextureAtlasSprite(id){};s.setIconWidth(width);s.setIconHeight(height);s.initSprite(256,256,x,y,false);return s;}
 private static JsonArray data(int[] values){JsonArray a=new JsonArray();for(int n:values)a.add(n);return a;}
 private static JsonArray extent(TextureAtlasSprite s){JsonArray a=new JsonArray();a.add(s.getMinU());a.add(s.getMaxU());a.add(s.getMinV());a.add(s.getMaxV());return a;}
 public static void main(String[] args)throws Exception {
  net.minecraft.init.Bootstrap.register();Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field.get(null);Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);for(Field f:Minecraft.class.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==Minecraft.class){f.setAccessible(true);f.set(null,mc);}
  TextureAtlasSprite target=sprite("replacement",96,64,32,16);TextureMap atlas=new TextureMap(""){@Override public TextureAtlasSprite getAtlasSprite(String name){return target;}};for(Field f:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&f.getType()==TextureMap.class){f.setAccessible(true);f.set(mc,atlas);}
  BlockPropulsionLight block=new BlockPropulsionLight("material_oracle",false,1);Gson gson=new Gson();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();int cases=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(String name:new String[]{"thrusters/side","thrusters/top","thrusters/rear","thrusters/cavity","thrusters/rocket_on","thrusters/rocket_stream","hover/repulsor_on","hover/repulsor_stream","thrusters/rocket_off","other"})for(boolean extended:new boolean[]{false,true})for(int brightness=0;brightness<16;brightness++)for(int choice:new int[]{9,64,14}){
    TextureAtlasSprite source=sprite("vandorlabs:blocks/"+name,16,32,16,32);VertexFormat format=new VertexFormat(DefaultVertexFormats.BLOCK);if(extended)format.addElement(new VertexFormatElement(1,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.GENERIC,4));int stride=format.getIntegerSize(),uv=format.getUvOffsetById(0)/4,color=format.getColorOffset()/4;int[] raw=new int[4*stride];for(int i=0;i<raw.length;i++)raw[i]=0x54320000+i;for(int v=0;v<4;v++){raw[v*stride+color]=new int[]{0xffe72b19,0x7f139be1,0x001e23f7,0xa4ffffff}[v];raw[v*stride+uv]=Float.floatToRawIntBits(source.getInterpolatedU(new float[]{0,3.75F,16,.00001F}[v]));raw[v*stride+uv+1]=Float.floatToRawIntBits(source.getInterpolatedV(new float[]{16,0,7.125F,.00001F}[v]));}
    BakedQuad input=new BakedQuad(raw,3,EnumFacing.SOUTH,source,false,format);List<BakedQuad> list=Collections.singletonList(input);IBakedModel delegate=(IBakedModel)Proxy.newProxyInstance(IBakedModel.class.getClassLoader(),new Class[]{IBakedModel.class},(proxy,method,arguments)->method.getName().equals("getQuads")?list:method.getReturnType()==boolean.class?false:null);IExtendedBlockState state=((IExtendedBlockState)block.getDefaultState()).withProperty(BlockPropulsionLight.SIDE_TEXTURE,choice<0?null:choice).withProperty(BlockPropulsionLight.BRIGHTNESS,brightness);List<BakedQuad> actual=new PropulsionSideModel(delegate).getQuads(state,null,0);BakedQuad q=actual.get(0);JsonObject r=new JsonObject();r.addProperty("sprite",source.getIconName());r.addProperty("brightness",brightness);r.addProperty("choice",choice);r.addProperty("stride",stride);r.addProperty("uv",uv);r.addProperty("color",color);r.addProperty("identity",actual==list);r.addProperty("target",q.getSprite()==target);r.add("sourceExtent",extent(source));r.add("targetExtent",extent(target));r.add("input",data(raw));r.add("output",data(q.getVertexData()));out.println(gson.toJson(r));cases++;
   }
  }
  SegmentedReferenceGolden.check(file,"0067baaa4bbd20e9d2cb3068dea91d987331f1061996674996ac4e6fd9a58279");
  System.out.println("Actual propulsion material PASS cases="+cases+" sourceWrapper/vanilla+extended/16brightness/ABGR/subUV/trim/identity");
 }
}
