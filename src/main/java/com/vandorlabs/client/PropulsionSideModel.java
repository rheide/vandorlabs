package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Replaces the propulsion housing material while preserving trim and emitters. */
public final class PropulsionSideModel implements IBakedModel {
    private static final String PREFIX = "vandorlabs:blocks/thrusters/";
    private final IBakedModel delegate;

    public PropulsionSideModel(IBakedModel delegate) { this.delegate = delegate; }

    @Override public List<BakedQuad> getQuads(@Nullable IBlockState state,
            @Nullable EnumFacing side, long rand) {
        List<BakedQuad> original = delegate.getQuads(state, side, rand);
        if (!(state instanceof IExtendedBlockState)) return original;
        Integer choice = ((IExtendedBlockState) state).getValue(BlockPropulsionLight.SIDE_TEXTURE);
        Integer brightness=((IExtendedBlockState)state).getValue(BlockPropulsionLight.BRIGHTNESS);
        if(brightness==null)brightness=15;
        if ((choice == null || choice == ScreenHousingTextures.INDUSTRIAL_BLOCK) && brightness==15)return original;
        TextureAtlasSprite replacement = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(ScreenHousingTextures.texture(choice));
        List<BakedQuad> result = new ArrayList<>(original.size());
        for (BakedQuad quad : original) {
            TextureAtlasSprite source = quad.getSprite();
            if (source == null || !quad.getFormat().hasUvOffset(0)) {
                result.add(quad);
                continue;
            }
            int[] data = quad.getVertexData().clone();
            int stride = quad.getFormat().getIntegerSize();
            int uv = quad.getFormat().getUvOffsetById(0) / 4;
            boolean housing=isHousing(source.getIconName());
            boolean replace=housing && choice!=null && choice!=ScreenHousingTextures.INDUSTRIAL_BLOCK;
            TextureAtlasSprite target=replace?replacement:source;
            for (int vertex = 0; vertex < 4; vertex++) {
                if(source.getIconName().startsWith(PREFIX) && (source.getIconName().endsWith("_on") || source.getIconName().endsWith("_stream")) && brightness<15 && quad.getFormat().hasColor()) {
                    int color=vertex*stride+quad.getFormat().getColorOffset()/4;
                    int old=data[color],next=old&0xFF000000;
                    float factor=.20F+.80F*brightness/15F;
                    for(int c=0;c<3;c++)next|=Math.round((old>>>(c*8)&255)*factor)<<(c*8);
                    data[color]=next;
                }
                int index = vertex * stride + uv;
                float u = source.getUnInterpolatedU(Float.intBitsToFloat(data[index]));
                float v = source.getUnInterpolatedV(Float.intBitsToFloat(data[index + 1]));
                data[index] = Float.floatToRawIntBits(target.getInterpolatedU(u));
                data[index + 1] = Float.floatToRawIntBits(target.getInterpolatedV(v));
            }
            result.add(new BakedQuad(data, quad.getTintIndex(), quad.getFace(),
                    target, quad.shouldApplyDiffuseLighting(), quad.getFormat()));
        }
        return result;
    }

    static boolean isHousing(String sprite) {
        return (PREFIX+"side").equals(sprite) || (PREFIX+"top").equals(sprite)
                || (PREFIX+"rear").equals(sprite) || (PREFIX+"cavity").equals(sprite);
    }

    @Override public boolean isAmbientOcclusion() { return delegate.isAmbientOcclusion(); }
    @Override public boolean isGui3d() { return delegate.isGui3d(); }
    @Override public boolean isBuiltInRenderer() { return delegate.isBuiltInRenderer(); }
    @Override public TextureAtlasSprite getParticleTexture() { return delegate.getParticleTexture(); }
    @Override public ItemCameraTransforms getItemCameraTransforms() {
        return delegate.getItemCameraTransforms();
    }
    @Override public ItemOverrideList getOverrides() { return delegate.getOverrides(); }
}
