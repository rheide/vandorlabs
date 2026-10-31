package com.vandorlabs.client;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import java.util.*;
/** Prepared glazing geometry follows the same moving leaf and shader dispatch as native glass. */
final class DoorGlassModel implements IBakedModel {
 private static final com.google.common.cache.Cache<Key,DoorGlassModel> CACHE=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(8*1024*1024).weigher((Key k,DoorGlassModel v)->v.bytes).build();
 private static final class Key {final IBakedModel model;final DoorGlassAppearance finish;final double scale;Key(IBakedModel m,DoorGlassAppearance a,double s){model=m;finish=a;scale=s;}public int hashCode(){return Objects.hash(System.identityHashCode(model),finish,scale);}public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return model==k.model&&finish.equals(k.finish)&&scale==k.scale;}}
 private final IBakedModel base;private final List<List<BakedQuad>> quads=new ArrayList<>(7);private int bytes=1024;private DoorQuadPlan plan;private boolean planned;
 static IBakedModel get(IBakedModel base,TileEntitySpaceDoor tile,double scale){if(!tile.hasGlassOverride())return base;Key key=new Key(base,tile.glassAppearance(),scale);DoorGlassModel result=CACHE.getIfPresent(key);if(result==null){result=new DoorGlassModel(key);if(result.bytes<=8*1024*1024)CACHE.put(key,result);}return result;}
 private DoorGlassModel(Key key){base=key.model;DoorGlassAppearance a=key.finish;TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassMaterials.texture(a.material,a.shade,1,a.tint));double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
  for(int side=-1;side<6;side++)for(BakedQuad q:base.getQuads(null,side<0?null:EnumFacing.getFront(side),0)){int[] d=q.getVertexData();int stride=d.length/4;for(int i=0;i<4;i++){double x=Float.intBitsToFloat(d[i*stride]),y=Float.intBitsToFloat(d[i*stride+1]);bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.max(bounds[2],x);bounds[3]=Math.max(bounds[3],y);}}
  boolean fit=com.vandorlabs.render.TextureLayoutMode.fit(a.layout,(bounds[2]-bounds[0])*key.scale,(bounds[3]-bounds[1])*key.scale,UnifiedTextureSprites.aspect(sprite));int alpha=Math.round(GlassMaterials.opacity(a.material,a.tint)*255);
  for(int side=-1;side<6;side++){List<BakedQuad> result=new ArrayList<>();for(BakedQuad q:base.getQuads(null,side<0?null:EnumFacing.getFront(side),0)){
   List<BakedQuad> mapped;
   if(!fit)mapped=DoorMaterialQuads.tile(q,sprite,key.scale);
   else{int[] d=q.getVertexData().clone();int stride=d.length/4,offset=q.getFormat().getUvOffsetById(0)/4;for(int i=0;i<4;i++){double x=Float.intBitsToFloat(d[i*stride]),y=Float.intBitsToFloat(d[i*stride+1]);d[i*stride+offset]=Float.floatToRawIntBits(sprite.getInterpolatedU((x-bounds[0])/Math.max(1e-9,bounds[2]-bounds[0])*16));d[i*stride+offset+1]=Float.floatToRawIntBits(sprite.getInterpolatedV((bounds[3]-y)/Math.max(1e-9,bounds[3]-bounds[1])*16));}mapped=Collections.singletonList(new BakedQuad(d,q.getTintIndex(),q.getFace(),sprite,q.shouldApplyDiffuseLighting(),q.getFormat()));}
   for(BakedQuad m:mapped){int[] d=m.getVertexData().clone();int stride=d.length/4,color=m.getFormat().getColorOffset()/4;for(int i=0;i<4;i++)d[i*stride+color]=(d[i*stride+color]&0xffffff)|((((d[i*stride+color]>>>24)*alpha+127)/255)<<24);result.add(new BakedQuad(d,m.getTintIndex(),m.getFace(),sprite,m.shouldApplyDiffuseLighting(),m.getFormat()));bytes+=d.length*8+256;}
  }quads.add(Collections.unmodifiableList(result));}
 }
 DoorQuadPlan plan(){if(!planned){plan=DoorQuadPlan.prepare(this);planned=true;}return plan;}
 int memoryBytes(){return bytes;}
 static void clear(){CACHE.invalidateAll();}
 public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return quads.get(side==null?0:side.getIndex()+1);}public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}public boolean isGui3d(){return base.isGui3d();}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
}
