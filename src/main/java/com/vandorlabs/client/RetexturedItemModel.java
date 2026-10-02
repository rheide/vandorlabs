package com.vandorlabs.client;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** Reuses the configured shape without generating a JSON model for every material. */
public final class RetexturedItemModel implements IBakedModel {
    private final IBakedModel base;
    private final TextureAtlasSprite sprite;
    private final boolean lightOnly;
    public RetexturedItemModel(IBakedModel base,TextureAtlasSprite sprite){this(base,sprite,false);}
    public RetexturedItemModel(IBakedModel base,TextureAtlasSprite sprite,boolean lightOnly){this.base=base;this.sprite=sprite;this.lightOnly=lightOnly;}
    @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){
        List<BakedQuad> result=new ArrayList<>();
        for(BakedQuad quad:base.getQuads(state,side,seed)) {
            if(lightOnly && !isLamp(quad.getSprite().getIconName())){result.add(quad);continue;}
            int[] data=quad.getVertexData().clone();int stride=data.length/4;
            for(int v=0;v<4;v++) {
                float u=quad.getSprite().getUnInterpolatedU(Float.intBitsToFloat(data[v*stride+4]));
                float t=quad.getSprite().getUnInterpolatedV(Float.intBitsToFloat(data[v*stride+5]));
                data[v*stride+4]=Float.floatToRawIntBits(sprite.getInterpolatedU(u));
                data[v*stride+5]=Float.floatToRawIntBits(sprite.getInterpolatedV(t));
            }
            result.add(new BakedQuad(data,quad.getTintIndex(),quad.getFace(),sprite,quad.shouldApplyDiffuseLighting(),quad.getFormat()));
        }
        return result;
    }
    private static boolean isLamp(String name){for(String id:com.vandorlabs.tiles.ProgrammableLightTextures.IDS)if(name.startsWith("vandorlabs:blocks/"+id+"_"))return true;return false;}
    @Override public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    @Override public boolean isGui3d(){return base.isGui3d();}
    @Override public boolean isBuiltInRenderer(){return base.isBuiltInRenderer();}
    @Override public TextureAtlasSprite getParticleTexture(){return sprite;}
    @Override public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}
    @Override public ItemOverrideList getOverrides(){return base.getOverrides();}
}
