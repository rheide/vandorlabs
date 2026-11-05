package com.vandorlabs.neoforge.fluid.client;

import com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Section workers consume immutable faces and the atlas lookup supplied by their own bake. */
public final class NativeFluidModel extends BakedModelWrapper<BakedModel> {
 private static final ChunkRenderTypeSet FLUID=ChunkRenderTypeSet.of(RenderType.translucent());
 private static final com.google.common.cache.Cache<Key,List<BakedQuad>> QUADS=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(8*1024*1024)
  .weigher((Key key,List<BakedQuad> quads)->(int)Math.min(Integer.MAX_VALUE,1024L+key.faces.size()*320L+quads.size()*192L)).build();
 /** Keys exclude Contact, slots, opaque NBT and volume graphs. Atlas identity preserves old bake snapshots. */
 private static final class Key {
  final List<NativeFluidRenderSnapshot.Face> faces;final Function<Material,TextureAtlasSprite> textures;
  Key(List<NativeFluidRenderSnapshot.Face> faces,Function<Material,TextureAtlasSprite> textures){this.faces=faces;this.textures=textures;}
  @Override public int hashCode(){return 31*System.identityHashCode(faces)+System.identityHashCode(textures);}
  @Override public boolean equals(Object other){return other instanceof Key key&&faces==key.faces&&textures==key.textures;}
 }
 private final Function<Material,TextureAtlasSprite> textures;
 private NativeFluidModel(BakedModel parent,Function<Material,TextureAtlasSprite> textures){super(parent);this.textures=Objects.requireNonNull(textures);}
 public static BakedModel wrap(BakedModel housing,Function<Material,TextureAtlasSprite> textures){return new NativeFluidModel(housing,textures);}
 public static void clear(){QUADS.invalidateAll();}
 @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){var base=super.getRenderTypes(state,random,data);var snapshot=data.get(NativeFluidRenderSnapshot.DATA);return snapshot==null||snapshot.faces.isEmpty()?base:ChunkRenderTypeSet.union(base,FLUID);}
 @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
  var base=state==null||layer==null||super.getRenderTypes(state,random,data).contains(layer)?super.getQuads(state,side,random,data,layer):List.<BakedQuad>of();
  var snapshot=data.get(NativeFluidRenderSnapshot.DATA);if(side!=null||snapshot==null||snapshot.faces.isEmpty()||layer!=null&&layer!=RenderType.translucent())return base;
  List<BakedQuad> fluid;try{fluid=QUADS.get(new Key(snapshot.faces,textures),()->bake(snapshot.faces,textures));}catch(java.util.concurrent.ExecutionException failure){throw new IllegalStateException("Fluid quad bake failed",failure.getCause());}
  if(base.isEmpty())return fluid;List<BakedQuad> all=new ArrayList<>(base.size()+fluid.size());all.addAll(base);all.addAll(fluid);return all;
 }
 private static List<BakedQuad> bake(List<NativeFluidRenderSnapshot.Face> faces,Function<Material,TextureAtlasSprite> textures){List<BakedQuad> result=new ArrayList<>(faces.size());
  for(var face:faces){var sprite=textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS,face.texture()));int[] packed=new int[32];int normal=((int)Math.round(face.nx()*127)&255)|(((int)Math.round(face.ny()*127)&255)<<8)|(((int)Math.round(face.nz()*127)&255)<<16);int color=face.argb(),abgr=(color&0xFF00FF00)|((color>>16)&255)|((color&255)<<16);
   for(int i=0;i<4;i++){var v=face.vertices().get(i);double u=face.axis()==0?v.z:v.x,t=face.axis()==1?v.z:1-v.y;int n=i*8;packed[n]=Float.floatToRawIntBits((float)v.x);packed[n+1]=Float.floatToRawIntBits((float)v.y);packed[n+2]=Float.floatToRawIntBits((float)v.z);packed[n+3]=abgr;packed[n+4]=Float.floatToRawIntBits(sprite.getU((float)u));packed[n+5]=Float.floatToRawIntBits(sprite.getV((float)t));packed[n+6]=face.packedLight();packed[n+7]=normal;}
   result.add(new BakedQuad(packed,-1,Direction.from3DDataValue(face.facing()),sprite,false,false));
  }return List.copyOf(result);
 }
}
