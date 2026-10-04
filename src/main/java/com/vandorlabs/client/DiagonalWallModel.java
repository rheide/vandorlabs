package com.vandorlabs.client;

import com.vandorlabs.blocks.DiagonalWallState;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.Collections;
import java.util.List;

/** Static diagonal walls are emitted once into Minecraft's chunk buffers. */
public final class DiagonalWallModel implements IBakedModel {
    private final IBakedModel delegate;
    public DiagonalWallModel(IBakedModel delegate){this.delegate=delegate;}
    @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed) {
        if(side!=null || !(state instanceof IExtendedBlockState))return Collections.emptyList();
        DiagonalWallState shape=((IExtendedBlockState)state).getValue(DiagonalWallState.PROPERTY);
        if(shape==null)return Collections.emptyList();
        TextureAtlasSprite wall=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(shape.texture));
        TextureAtlasSprite metal=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
        return bake(shape,wall,metal);
    }
    static List<BakedQuad> bake(DiagonalWallState shape,TextureAtlasSprite wall,TextureAtlasSprite metal) {
        return bake(shape,wall,metal,true);
    }
    static List<BakedQuad> bake(DiagonalWallState shape,TextureAtlasSprite wall,TextureAtlasSprite metal,boolean merge) {
        DiagonalWallQuadCapture capture=new DiagonalWallQuadCapture(shape.facing,merge);
        TEAnimatedScreenSelector.renderDiagonalWall(capture,wall,metal,shape.inverted,shape.corner,
                shape.span,shape.fill,shape.halfHeight?(shape.inverted?-8:0):0,
                shape.halfHeight?(shape.inverted?8:16):16,
                shape.halfHeight?(shape.inverted?8:0):Double.NaN,
                shape.lowerEnd,shape.upperEnd,shape.hideLower,shape.hideUpper,shape.clip());
        return capture.finish();
    }
    @Override public boolean isAmbientOcclusion(){return false;}
    @Override public boolean isGui3d(){return delegate.isGui3d();}
    @Override public boolean isBuiltInRenderer(){return false;}
    @Override public TextureAtlasSprite getParticleTexture(){return delegate.getParticleTexture();}
    @Override public ItemCameraTransforms getItemCameraTransforms(){return delegate.getItemCameraTransforms();}
    @Override public ItemOverrideList getOverrides(){return delegate.getOverrides();}
}
