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
            double[] u=new double[4],v=new double[4];com.vandorlabs.render.ControlMount.Point[] positions=new com.vandorlabs.render.ControlMount.Point[4];
            for(int i=0;i<4;i++){u[i]=sprite.getUnInterpolatedU(Float.intBitsToFloat(data[i*stride+4]));v[i]=sprite.getUnInterpolatedV(Float.intBitsToFloat(data[i*stride+5]));positions[i]=new com.vandorlabs.render.ControlMount.Point(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]));}
            double[][] uv=com.vandorlabs.render.HousingItemTextureMapping.map(positions,u,v,mode,UnifiedTextureSprites.aspect(sprite));
            for(int i=0;i<4;i++){data[i*stride+4]=Float.floatToRawIntBits(sprite.getInterpolatedU(uv[i][0]));data[i*stride+5]=Float.floatToRawIntBits(sprite.getInterpolatedV(uv[i][1]));}
            result.add(new BakedQuad(data,quad.getTintIndex(),quad.getFace(),sprite,quad.shouldApplyDiffuseLighting(),quad.getFormat()));
        }
        return result;
    }
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}public boolean isGui3d(){return base.isGui3d();}public boolean isBuiltInRenderer(){return base.isBuiltInRenderer();}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
}
