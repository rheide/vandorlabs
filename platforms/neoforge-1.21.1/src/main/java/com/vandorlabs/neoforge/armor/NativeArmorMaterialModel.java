package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** The authored padded inventory silhouette with selected material in place of its placeholder. */
final class NativeArmorMaterialModel extends BakedModelWrapper<BakedModel> {
    private final List<BakedQuad> quads;
    private final TextureAtlasSprite sprite;
    private final List<BakedModel> renderPasses = List.of(this);

    NativeArmorMaterialModel(BakedModel geometry, ModernFinishCatalog.Entry material) {
        super(geometry);
        sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(ResourceLocation.parse(material.texture()));
        quads = geometry.getQuads(null, null, RandomSource.create(0)).stream()
            .map(quad -> retexture(quad, material.u(), material.v()))
            .toList();
    }

    private BakedQuad retexture(BakedQuad quad, float usedU, float usedV) {
        var original = quad.getSprite();
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int offset = 0; offset < vertices.length; offset += stride) {
            float u = (Float.intBitsToFloat(vertices[offset + 4]) - original.getU0()) / (original.getU1() - original.getU0());
            float v = (Float.intBitsToFloat(vertices[offset + 5]) - original.getV0()) / (original.getV1() - original.getV0());
            vertices[offset + 4] = Float.floatToRawIntBits(sprite.getU(u * usedU));
            vertices[offset + 5] = Float.floatToRawIntBits(sprite.getV(v * usedV));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), sprite, quad.isShade(), quad.hasAmbientOcclusion());
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return side == null ? quads : List.of();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data,
                                     net.minecraft.client.renderer.RenderType layer) {
        return getQuads(state, side, random);
    }

    @Override
    public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,
                                    com.mojang.blaze3d.vertex.PoseStack pose, boolean leftHand) {
        getTransforms().getTransform(context).apply(leftHand, pose);
        return this;
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        return renderPasses;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() { return sprite; }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) { return sprite; }
}
