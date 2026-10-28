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
    private static final int MAX_BYTES=8*1024*1024;
    private static int retainedBytes;
    private static final Map<Key,FrameDoorModel> CACHE=new LinkedHashMap<Key,FrameDoorModel>(64,.75F,true);
    private final IBakedModel base;private int bytes=256;private final List<List<BakedQuad>> quads=new ArrayList<>(7);
    private static final class Key {final IBakedModel base;final TextureAtlasSprite front,inner;Key(IBakedModel b,TextureAtlasSprite f,TextureAtlasSprite i){base=b;front=f;inner=i;}public int hashCode(){return Objects.hash(System.identityHashCode(base),System.identityHashCode(front),System.identityHashCode(inner));}public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return base==k.base&&front==k.front&&inner==k.inner;}}
    static IBakedModel get(IBakedModel model,FrameAppearance a){if(a.front<0&&a.interior<0)return model;net.minecraft.client.renderer.texture.TextureMap atlas=Minecraft.getMinecraft().getTextureMapBlocks();Key key=new Key(model,atlas.getAtlasSprite(a.rail("vandorlabs:blocks/space_doors/medium/double_frame_metal")),atlas.getAtlasSprite(a.interior("vandorlabs:blocks/programmable_glass/metal_side")));FrameDoorModel result=CACHE.get(key);if(result==null){result=new FrameDoorModel(key,a.front>=0,a.interior>=0);if(result.bytes<=MAX_BYTES){CACHE.put(key,result);retainedBytes+=result.bytes;while(CACHE.size()>256||retainedBytes>MAX_BYTES){Iterator<Map.Entry<Key,FrameDoorModel>> old=CACHE.entrySet().iterator();retainedBytes-=old.next().getValue().bytes;old.remove();}}}return result;}
    private FrameDoorModel(Key key,boolean front,boolean inner){
        base=key.base;double minX=Double.POSITIVE_INFINITY,maxX=-minX,minY=minX,maxY=-minX;
        for(int side=-1;side<6;side++)for(BakedQuad q:base.getQuads(null,side<0?null:EnumFacing.getFront(side),0))if(q.getSprite().getIconName().endsWith("/double_frame_metal")){
            int[] d=q.getVertexData();int stride=d.length/4;for(int v=0;v<4;v++){double x=Float.intBitsToFloat(d[v*stride]),y=Float.intBitsToFloat(d[v*stride+1]);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        }
        for(int side=-1;side<6;side++){
            List<BakedQuad> list=new ArrayList<>();
            for(BakedQuad q:base.getQuads(null,side<0?null:EnumFacing.getFront(side),0)){
                String name=q.getSprite().getIconName();boolean face=front&&name.endsWith("/double_frame_metal");
                TextureAtlasSprite target=face?key.front:inner&&name.endsWith("/metal_side")?key.inner:null;
                if(target==null){bytes+=8;list.add(q);continue;}
                int[] data=q.getVertexData().clone();bytes+=data.length*4+64;int stride=data.length/4;
                double loX=Double.POSITIVE_INFINITY,hiX=-loX,loY=loX,hiY=-loX,loZ=loX,hiZ=-loX;
                for(int v=0;v<4;v++){double x=Float.intBitsToFloat(data[v*stride]),y=Float.intBitsToFloat(data[v*stride+1]),z=Float.intBitsToFloat(data[v*stride+2]);loX=Math.min(loX,x);hiX=Math.max(hiX,x);loY=Math.min(loY,y);hiY=Math.max(hiY,y);loZ=Math.min(loZ,z);hiZ=Math.max(hiZ,z);}
                boolean horizontal=q.getFace().getAxis()==EnumFacing.Axis.Y||q.getFace().getAxis()==EnumFacing.Axis.Z&&hiX-loX>=hiY-loY;
                int edge=horizontal?((loY+hiY)/2>=(minY+maxY)/2?FrameEdgeTextures.TOP:FrameEdgeTextures.BOTTOM):((loX+hiX)/2>=(minX+maxX)/2?FrameEdgeTextures.RIGHT:FrameEdgeTextures.LEFT);
                TextureAtlasSprite cut=FrameEdgeTextures.edge(target,edge);boolean authored=cut!=target||target.getIconName().contains("/frames/");target=cut;
                for(int v=0;v<4;v++){
                    double x=Float.intBitsToFloat(data[v*stride]),y=Float.intBitsToFloat(data[v*stride+1]),z=Float.intBitsToFloat(data[v*stride+2]);
                    double u=q.getSprite().getUnInterpolatedU(Float.intBitsToFloat(data[v*stride+4])),tv=q.getSprite().getUnInterpolatedV(Float.intBitsToFloat(data[v*stride+5]));
                    if(authored&&maxX>minX&&maxY>minY){
                        u=horizontal?(x-minX)/(maxX-minX)*16:(maxY-y)/(maxY-minY)*16;
                        if(hiZ-loZ>1e-6)tv=(z-loZ)/(hiZ-loZ)*16;
                        else if(horizontal&&hiY>loY)tv=(hiY-y)/(hiY-loY)*16;
                        else if(hiX>loX)tv=(x-loX)/(hiX-loX)*16;
                    }
                    data[v*stride+4]=Float.floatToRawIntBits(target.getInterpolatedU(u));data[v*stride+5]=Float.floatToRawIntBits(target.getInterpolatedV(tv));
                }
                list.add(new BakedQuad(data,q.getTintIndex(),q.getFace(),target,q.shouldApplyDiffuseLighting(),q.getFormat()));
            }
            quads.add(Collections.unmodifiableList(list));
        }
    }
    static void clear(){CACHE.clear();retainedBytes=0;}
    public List<BakedQuad> getQuads(IBlockState s,EnumFacing side,long seed){return quads.get(side==null?0:side.getIndex()+1);}public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}public boolean isGui3d(){return base.isGui3d();}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
}
