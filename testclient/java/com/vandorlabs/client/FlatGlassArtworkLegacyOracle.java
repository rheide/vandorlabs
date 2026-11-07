package com.vandorlabs.client;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;

/** Actual Forge authored default frame and configured item models, kept distinct from selected-frame emission. */
public final class FlatGlassArtworkLegacyOracle {
 private static JsonArray extent(TextureAtlasSprite sprite){JsonArray result=new JsonArray();result.add(sprite.getMinU());result.add(sprite.getMaxU());result.add(sprite.getMinV());result.add(sprite.getMaxV());return result;}
 public static void main(String[] args)throws Exception {PropulsionArtworkLegacyOracle.initialize();JsonObject state;try(Reader reader=new InputStreamReader(FlatGlassArtworkLegacyOracle.class.getResourceAsStream("/assets/vandorlabs/blockstates/programmable_glass.json"),"UTF-8")){state=new JsonParser().parse(reader).getAsJsonObject();}Set<String> poses=new TreeSet<>();for(JsonElement raw:state.getAsJsonArray("multipart")){JsonObject apply=raw.getAsJsonObject().getAsJsonObject("apply");poses.add(apply.get("model").getAsString()+"@"+(apply.has("y")?apply.get("y").getAsInt():0));}for(int size=0;size<2;size++)for(String shade:com.vandorlabs.tiles.GlassTextures.IDS)for(int tint=0;tint<4;tint++)for(int frame=0;frame<2;frame++)poses.add("vandorlabs:item/configured/glass_regular_"+size+"_"+shade+"_t"+tint+"_f"+frame+"@0");File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int models=0,quads=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(String pose:poses){int marker=pose.lastIndexOf('@');String source=pose.substring(0,marker);int y=Integer.parseInt(pose.substring(marker+1));IBakedModel model=PropulsionArtworkLegacyOracle.model(source,0,y);JsonObject row=new JsonObject();row.addProperty("source",source);row.addProperty("y",y);row.addProperty("ao",model.isAmbientOcclusion());JsonArray emitted=new JsonArray();for(EnumFacing side:new EnumFacing[]{null,EnumFacing.DOWN,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST})for(BakedQuad quad:model.getQuads(null,side,0)){JsonObject face=new JsonObject();face.addProperty("sprite",quad.getSprite().getIconName());face.add("extent",extent(quad.getSprite()));face.addProperty("face",quad.getFace().getIndex());face.addProperty("cull",side==null?-1:side.getIndex());face.addProperty("shade",quad.shouldApplyDiffuseLighting());face.addProperty("tint",quad.getTintIndex());int stride=quad.getFormat().getIntegerSize(),normal=quad.getFormat().getNormalOffset()/4;int[] data=quad.getVertexData();JsonArray words=new JsonArray();for(int v=0;v<4;v++){for(int axis=0;axis<6;axis++)words.add(data[v*stride+axis]);words.add(0);words.add(quad.getFormat().hasNormal()?data[v*stride+normal]&0xffffff:0);}face.add("vertices",words);emitted.add(face);quads++;}row.add("quads",emitted);out.println(gson.toJson(row));models++;}}
  SegmentedReferenceGolden.check(file,"95c3d3040568cf3c81a8d017465e677de0e6bdcbc14d992e7c1eea0b46cfc00c");
  System.out.println("Actual flat glass authored models PASS models="+models+" quads="+quads+" defaultParts3sizes/3depths/2rotations +304configuredItemVariants");
 }
}
