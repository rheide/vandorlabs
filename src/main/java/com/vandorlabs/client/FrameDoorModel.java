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
    static boolean applies(FrameAppearance appearance,int part){return part==0&&(appearance.front>=0||appearance.interior>=0)||part==1&&appearance.interior>=0;}
    static boolean interiorQuad(BakedQuad quad,IBakedModel model){
        String name=quad.getSprite().getIconName(),particle=model.getParticleTexture().getIconName();boolean broad=quad.getFace().getAxis()==EnumFacing.Axis.Z;
        return name.endsWith("/metal_side")||name.endsWith("/double_frame_metal")&&!broad
                ||!broad&&name.equals(particle)&&(particle.endsWith("/light_alloy_hull")||particle.endsWith("/programmable_glass/door_side"));
    }
    private static final int MAX_BYTES=8*1024*1024;
    private static int retainedBytes;
    private static final Map<Key,FrameDoorModel> CACHE=new LinkedHashMap<Key,FrameDoorModel>(64,.75F,true);
    private final IBakedModel base;private int bytes=256;private final List<List<BakedQuad>> quads=new ArrayList<>(7);
    private static final class Key {final IBakedModel base;final TextureAtlasSprite front,inner;final int flags;final double scale;Key(IBakedModel b,TextureAtlasSprite f,TextureAtlasSprite i,int flags,double scale){base=b;front=f;inner=i;this.flags=flags;this.scale=scale;}public int hashCode(){return Objects.hash(System.identityHashCode(base),System.identityHashCode(front),System.identityHashCode(inner),flags,scale);}public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return base==k.base&&front==k.front&&inner==k.inner&&flags==k.flags&&scale==k.scale;}}
    static IBakedModel get(IBakedModel model,FrameAppearance a){return get(model,a,1);}
    static IBakedModel get(IBakedModel model,FrameAppearance a,double scale){return get(model,a,scale,name->Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name));}
    static IBakedModel get(IBakedModel model,FrameAppearance a,double scale,java.util.function.Function<String,TextureAtlasSprite> sprites){if(a.front<0&&a.interior<0)return model;Key key=new Key(model,sprites.apply(a.rail("vandorlabs:blocks/space_doors/medium/double_frame_metal")),sprites.apply(a.interior("vandorlabs:blocks/programmable_glass/metal_side")),(a.front>=0?1:0)|(a.interior>=0?2:0),scale);FrameDoorModel result=CACHE.get(key);if(result==null){result=new FrameDoorModel(key,a.front>=0,a.interior>=0);if(result.bytes<=MAX_BYTES){CACHE.put(key,result);retainedBytes+=result.bytes;while(CACHE.size()>256||retainedBytes>MAX_BYTES){Iterator<Map.Entry<Key,FrameDoorModel>> old=CACHE.entrySet().iterator();retainedBytes-=old.next().getValue().bytes;old.remove();}}}return result;}
    private FrameDoorModel(Key key,boolean front,boolean inner){
        base=key.base;double minX=Double.POSITIVE_INFINITY,maxX=-minX,minY=minX,maxY=-minX;
        for(int side=-1;side<6;side++)for(BakedQuad q:base.getQuads(null,side<0?null:EnumFacing.getFront(side),0))if(q.getSprite().getIconName().endsWith("/double_frame_metal")||interiorQuad(q,base)){
            int[] d=q.getVertexData();int stride=d.length/4;for(int v=0;v<4;v++){double x=Float.intBitsToFloat(d[v*stride]),y=Float.intBitsToFloat(d[v*stride+1]);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        }
        for(int side=-1;side<6;side++){
            List<BakedQuad> list=new ArrayList<>();
            for(BakedQuad q:base.getQuads(null,side<0?null:EnumFacing.getFront(side),0)){
                String name=q.getSprite().getIconName();boolean nativeFrame=name.endsWith("/double_frame_metal"),broad=q.getFace().getAxis()==EnumFacing.Axis.Z;boolean face=front&&nativeFrame&&broad;
                TextureAtlasSprite target=face?key.front:inner&&interiorQuad(q,base)?key.inner:null;
                if(target==null){bytes+=q.getVertexData().length*8+128;list.add(q);continue;}
                if(!FrameEdgeTextures.authored(target)){
                    BakedQuad untinted=new BakedQuad(q.getVertexData(),-1,q.getFace(),q.getSprite(),q.shouldApplyDiffuseLighting(),q.getFormat());
                    List<BakedQuad> tiled=DoorMaterialQuads.tile(untinted,target,key.scale);for(BakedQuad built:tiled)bytes+=built.getVertexData().length*8+256;list.addAll(tiled);continue;
                }
                int[] data=q.getVertexData().clone();bytes+=data.length*8+256;int stride=data.length/4;
                double loX=Double.POSITIVE_INFINITY,hiX=-loX,loY=loX,hiY=-loX,loZ=loX,hiZ=-loX;
                for(int v=0;v<4;v++){double x=Float.intBitsToFloat(data[v*stride]),y=Float.intBitsToFloat(data[v*stride+1]),z=Float.intBitsToFloat(data[v*stride+2]);loX=Math.min(loX,x);hiX=Math.max(hiX,x);loY=Math.min(loY,y);hiY=Math.max(hiY,y);loZ=Math.min(loZ,z);hiZ=Math.max(hiZ,z);}
                boolean horizontal=q.getFace().getAxis()==EnumFacing.Axis.Y||q.getFace().getAxis()==EnumFacing.Axis.Z&&hiX-loX>=hiY-loY;
                int edge=horizontal?((loY+hiY)/2>=(minY+maxY)/2?FrameEdgeTextures.TOP:FrameEdgeTextures.BOTTOM):((loX+hiX)/2>=(minX+maxX)/2?FrameEdgeTextures.RIGHT:FrameEdgeTextures.LEFT);
                TextureAtlasSprite cut=FrameEdgeTextures.edge(target,edge);boolean authored=FrameEdgeTextures.authored(target);target=cut;
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
                list.add(new BakedQuad(data,-1,q.getFace(),target,q.shouldApplyDiffuseLighting(),q.getFormat()));
            }
            quads.add(Collections.unmodifiableList(list));
        }
    }
    private DoorQuadPlan plan;private boolean planReady;private OpaqueDoorBatch.Mesh batch;private boolean batchReady;
    DoorQuadPlan plan(){if(!planReady){plan=DoorQuadPlan.prepare(this);planReady=true;}return plan;}
    OpaqueDoorBatch.Mesh batch(DoorRenderModels.Entry entry){if(!batchReady){batch=OpaqueDoorBatch.prepare(entry,this);batchReady=true;}return batch;}
    static void clear(){CACHE.clear();retainedBytes=0;}
    public List<BakedQuad> getQuads(IBlockState s,EnumFacing side,long seed){return quads.get(side==null?0:side.getIndex()+1);}public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}public boolean isGui3d(){return base.isGui3d();}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
}
