package com.vandorlabs.neoforge.propulsion;

import com.vandorlabs.propulsion.PropulsionMaterial;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Bake-only Source housing replacement; preserves authored sub-UVs and complete vertex payload. */
public final class NativePropulsionMaterials {
 private NativePropulsionMaterials(){}
 public static List<BakedQuad> apply(List<BakedQuad> original,int choice,int brightness,TextureAtlasSprite replacement){
  if(choice==9&&brightness==15)return original;
  var result=new ArrayList<BakedQuad>(original.size());
  for(var q:original){var source=q.getSprite();String name=source.contents().name().toString();boolean replace=choice!=9&&PropulsionMaterial.housing(name);var target=replace?Objects.requireNonNull(replacement):source;int[] data=q.getVertices().clone();int stride=data.length/4;if(data.length%4!=0||stride<8)throw new IllegalArgumentException("Malformed propulsion vertex record");
   for(int v=0;v<4;v++){int at=v*stride;if(brightness<15&&PropulsionMaterial.emitter(name))data[at+3]=PropulsionMaterial.color(data[at+3],brightness);data[at+4]=Float.floatToRawIntBits(PropulsionMaterial.remap(Float.intBitsToFloat(data[at+4]),source.getU0(),source.getU1(),target.getU0(),target.getU1()));data[at+5]=Float.floatToRawIntBits(PropulsionMaterial.remap(Float.intBitsToFloat(data[at+5]),source.getV0(),source.getV1(),target.getV0(),target.getV1()));}
   result.add(new BakedQuad(data,q.getTintIndex(),q.getDirection(),target,q.isShade(),q.hasAmbientOcclusion()));
  }
  return List.copyOf(result);
 }
}
