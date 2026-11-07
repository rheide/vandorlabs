package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.client.NativeDoorSpriteSource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Bake-owned authored geometry/correction/content mapping shared by Door world and item adapters. */
public final class NativeDoorArtwork {
 private NativeDoorArtwork(){}
 public static float usedU(TextureAtlasSprite sprite){return sprite.contents() instanceof NativeDoorSpriteSource.Contents contents?contents.usedU():1;}
 public static float usedV(TextureAtlasSprite sprite){return sprite.contents() instanceof NativeDoorSpriteSource.Contents contents?contents.usedV():1;}
 public static JsonArray elements(String model){return elements(model,0);}
 private static JsonArray elements(String model,int depth){
  if(depth>16)throw new IllegalArgumentException("Cyclic Door model "+model);String[] name=model.split(":",2);String path="/assets/"+name[0]+"/models/"+name[1]+".json";var input=NativeDoorArtwork.class.getResourceAsStream(path);if(input==null)throw new IllegalArgumentException("Missing authored Door model "+model);
  try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){var object=JsonParser.parseReader(reader).getAsJsonObject();return object.has("elements")?object.getAsJsonArray("elements"):elements(object.get("parent").getAsString(),depth+1);}catch(java.io.IOException failure){throw new IllegalStateException("Door model "+model,failure);}
 }
 /** Compare active inherited geometry before applying builtin float correction to pack models. */
 public static boolean canonicalGeometry(String model,net.minecraft.server.packs.resources.ResourceManager resources){return canonicalGeometry(model,model,resources);}
 public static boolean canonicalGeometry(String builtin,String active,net.minecraft.server.packs.resources.ResourceManager resources){try{return elements(builtin).equals(activeElements(active,resources,0));}catch(java.io.IOException|RuntimeException unavailable){return false;}}
 private static JsonArray activeElements(String model,net.minecraft.server.packs.resources.ResourceManager resources,int depth)throws java.io.IOException{
  if(depth>16)throw new IllegalArgumentException("Cyclic active Door model");var id=net.minecraft.resources.ResourceLocation.parse(model);var resource=resources.getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"models/"+id.getPath()+".json")).orElseThrow();
  try(var reader=resource.openAsReader()){var object=JsonParser.parseReader(reader).getAsJsonObject();if(object.has("elements"))return object.getAsJsonArray("elements");return object.has("parent")?activeElements(object.get("parent").getAsString(),resources,depth+1):new JsonArray();}
 }
 public static boolean isStatic(BakedModel model){return NativeAuthoredControlBake.isStatic(model);}
 public static BakedModel correct(String model,BakedModel original){if(!isStatic(original))return original;return NativeAuthoredControlBake.correct(original,null,new NativeMountedControlGeometry.Authored(elements(model),0,0));}
 /** Null means a dynamic/resource-pack model needs its original renderer rather than an immutable guess. */
 public static List<BakedQuad> quads(String id,BakedModel original){
  if(!NativeAuthoredControlBake.isStatic(original))return null;var model=correct(id,original);var result=new ArrayList<BakedQuad>();for(Direction side:Direction.values())result.addAll(model.getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null));result.addAll(model.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null));return content(result);
 }
 public static List<BakedQuad> content(List<BakedQuad> source){var result=new ArrayList<BakedQuad>();for(var q:source){int[] data=q.getVertices().clone();if(data.length%4!=0||data.length/4<8)throw new IllegalArgumentException("Door quad vertex format");if(q.getSprite().contents() instanceof com.vandorlabs.neoforge.client.NativeDoorSpriteSource.Contents c){float u0=q.getSprite().getU0(),v0=q.getSprite().getV0();for(int i=0;i<4;i++){int at=i*(data.length/4);data[at+4]=Float.floatToRawIntBits(u0+(Float.intBitsToFloat(data[at+4])-u0)*c.usedU());data[at+5]=Float.floatToRawIntBits(v0+(Float.intBitsToFloat(data[at+5])-v0)*c.usedV());}}result.add(new BakedQuad(data,q.getTintIndex(),q.getDirection(),q.getSprite(),q.isShade(),q.hasAmbientOcclusion()));}return List.copyOf(result);}
}
