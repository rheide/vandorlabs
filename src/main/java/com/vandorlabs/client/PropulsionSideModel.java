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
        if (choice == null || choice == ScreenHousingTextures.INDUSTRIAL_BLOCK)
            return original;
        TextureAtlasSprite replacement = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(ScreenHousingTextures.texture(choice));
        List<BakedQuad> result = new ArrayList<>(original.size());
        for (BakedQuad quad : original) {
            TextureAtlasSprite source = quad.getSprite();
            if (source == null || !isHousing(source.getIconName())
                    || !quad.getFormat().hasUvOffset(0)) {
                result.add(quad);
                continue;
            }
            int[] data = quad.getVertexData().clone();
            int stride = quad.getFormat().getIntegerSize();
            int uv = quad.getFormat().getUvOffsetById(0) / 4;
            for (int vertex = 0; vertex < 4; vertex++) {
                int index = vertex * stride + uv;
                float u = source.getUnInterpolatedU(Float.intBitsToFloat(data[index]));
                float v = source.getUnInterpolatedV(Float.intBitsToFloat(data[index + 1]));
                data[index] = Float.floatToRawIntBits(replacement.getInterpolatedU(u));
                data[index + 1] = Float.floatToRawIntBits(replacement.getInterpolatedV(v));
            }
            result.add(new BakedQuad(data, quad.getTintIndex(), quad.getFace(),
                    replacement, quad.shouldApplyDiffuseLighting(), quad.getFormat()));
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
