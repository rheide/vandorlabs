package com.vandorlabs.client;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** Unit-sized housing items retain their geometry/transforms while matching the saved face layout. */
final class TextureLayoutItemModel implements IBakedModel {
    private final IBakedModel base;private final int mode;
    TextureLayoutItemModel(IBakedModel base,int mode){this.base=base;this.mode=mode;}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing face,long seed){
        List<BakedQuad> result=new ArrayList<>();
        for(BakedQuad quad:base.getQuads(state,face,seed)){
            int[] data=quad.getVertexData().clone();int stride=data.length/4;TextureAtlasSprite sprite=quad.getSprite();
            double[] u=new double[4],v=new double[4];double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=-minU,maxV=maxU,width=0,height=0;
            for(int i=0;i<4;i++){u[i]=sprite.getUnInterpolatedU(Float.intBitsToFloat(data[i*stride+4]));v[i]=sprite.getUnInterpolatedV(Float.intBitsToFloat(data[i*stride+5]));minU=Math.min(minU,u[i]);maxU=Math.max(maxU,u[i]);minV=Math.min(minV,v[i]);maxV=Math.max(maxV,v[i]);}
            for(int a=0;a<4;a++)for(int b=0;b<4;b++){
                double length=0;for(int axis=0;axis<3;axis++){double delta=Float.intBitsToFloat(data[a*stride+axis])-Float.intBitsToFloat(data[b*stride+axis]);length+=delta*delta;}length=Math.sqrt(length);
                if(Math.abs(v[a]-v[b])<1e-6 && Math.abs(u[a]-u[b])>1e-6)width=Math.max(width,length);
                if(Math.abs(u[a]-u[b])<1e-6 && Math.abs(v[a]-v[b])>1e-6)height=Math.max(height,length);
            }
            if(width>0 && height>0){
                double aspect=UnifiedTextureSprites.aspect(sprite);boolean fit=com.vandorlabs.render.TextureLayoutMode.fit(mode,width,height,aspect);
                for(int i=0;i<4;i++){double x=(u[i]-minU)/Math.max(1e-8,maxU-minU),y=(v[i]-minV)/Math.max(1e-8,maxV-minV);if(!fit){x=com.vandorlabs.render.TextureLayoutMode.tileU(x*width,aspect);y=com.vandorlabs.render.TextureLayoutMode.tileV(y*height,aspect);}data[i*stride+4]=Float.floatToRawIntBits(sprite.getInterpolatedU(x*16));data[i*stride+5]=Float.floatToRawIntBits(sprite.getInterpolatedV(y*16));}
            }
            result.add(new BakedQuad(data,quad.getTintIndex(),quad.getFace(),sprite,quad.shouldApplyDiffuseLighting(),quad.getFormat()));
        }
        return result;
    }
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}public boolean isGui3d(){return base.isGui3d();}public boolean isBuiltInRenderer(){return base.isBuiltInRenderer();}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
}
