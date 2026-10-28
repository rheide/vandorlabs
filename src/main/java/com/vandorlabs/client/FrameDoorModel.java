package com.vandorlabs.client;

import com.vandorlabs.tiles.FrameAppearance;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** Prepared frame-only recoloring preserves leaf, glass and extended vertex payloads. */
final class FrameDoorModel implements IBakedModel {
    private static final Map<Key,FrameDoorModel> CACHE=new LinkedHashMap<Key,FrameDoorModel>(64,.75F,true){protected boolean removeEldestEntry(Map.Entry<Key,FrameDoorModel> e){return size()>256;}};
    private final IBakedModel base;private final List<List<BakedQuad>> quads=new ArrayList<>(7);
    private static final class Key {final IBakedModel base;final TextureAtlasSprite front,inner;Key(IBakedModel b,TextureAtlasSprite f,TextureAtlasSprite i){base=b;front=f;inner=i;}public int hashCode(){return Objects.hash(System.identityHashCode(base),System.identityHashCode(front),System.identityHashCode(inner));}public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return base==k.base&&front==k.front&&inner==k.inner;}}
    static IBakedModel get(IBakedModel model,FrameAppearance a){if(a.front<0&&a.interior<0)return model;net.minecraft.client.renderer.texture.TextureMap atlas=Minecraft.getMinecraft().getTextureMapBlocks();Key key=new Key(model,atlas.getAtlasSprite(a.rail("vandorlabs:blocks/space_doors/medium/double_frame_metal")),atlas.getAtlasSprite(a.interior("vandorlabs:blocks/programmable_glass/metal_side")));FrameDoorModel result=CACHE.get(key);if(result==null){result=new FrameDoorModel(key,a.front>=0,a.interior>=0);CACHE.put(key,result);}return result;}
    private FrameDoorModel(Key key,boolean front,boolean inner){base=key.base;for(int side=-1;side<6;side++){List<BakedQuad> list=new ArrayList<>();for(BakedQuad q:base.getQuads(null,side<0?null:EnumFacing.getFront(side),0)){String name=q.getSprite().getIconName();TextureAtlasSprite target=front&&name.endsWith("/double_frame_metal")?key.front:inner&&name.endsWith("/metal_side")?key.inner:null;if(target==null){list.add(q);continue;}int[] data=q.getVertexData().clone();int stride=data.length/4;for(int v=0;v<4;v++){data[v*stride+4]=Float.floatToRawIntBits(target.getInterpolatedU(q.getSprite().getUnInterpolatedU(Float.intBitsToFloat(data[v*stride+4]))));data[v*stride+5]=Float.floatToRawIntBits(target.getInterpolatedV(q.getSprite().getUnInterpolatedV(Float.intBitsToFloat(data[v*stride+5]))));}list.add(new BakedQuad(data,q.getTintIndex(),q.getFace(),target,q.shouldApplyDiffuseLighting(),q.getFormat()));}quads.add(Collections.unmodifiableList(list));}}
    static void clear(){CACHE.clear();}
    public List<BakedQuad> getQuads(IBlockState s,EnumFacing side,long seed){return quads.get(side==null?0:side.getIndex()+1);}public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}public boolean isGui3d(){return base.isGui3d();}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
}
