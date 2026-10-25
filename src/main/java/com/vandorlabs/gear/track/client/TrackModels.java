package com.vandorlabs.gear.track.client;

import com.vandorlabs.gear.track.TrackAppearance;
import com.vandorlabs.gear.track.TrackBlock;
import com.vandorlabs.gear.track.TrackParts;
import com.vandorlabs.gear.track.TrackRegistry;

import com.vandorlabs.gear.track.core.TrackTopology;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Bakes the delivered native JSON parts once per resource reload. No world reads. */
@SideOnly(Side.CLIENT)
public final class TrackModels {
    private TrackModels() { }

    public static void reload(ModelBakeEvent event) {
        List<String> names = new ArrayList<String>();
        for (TrackTopology.Style style : TrackTopology.Style.values()) {
            for (int mask = 0; mask < 16; mask++) names.add(TrackParts.styleName(style) + "_" + mask);
        }
        Collections.addAll(names, "socket", "top_mount", "top_mount_high", "side_mount", "side_mount_low", "bridge_left", "bridge_right");
        Map<String, List<BakedQuad>> bakedParts = new HashMap<String, List<BakedQuad>>();
        for (String name : names) {
            ResourceLocation location = new ResourceLocation(com.vandorlabs.VandorLabs.MODID, "block/track/" + name);
            try {
                IModel source = ModelLoaderRegistry.getModel(location);
                for (int turn = 0; turn < 4; turn++) {
                    IBakedModel baked = source.bake(ModelRotation.getModelRotation(0, turn * 90), DefaultVertexFormats.ITEM,
                            texture -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString()));
                    List<BakedQuad> quads = new ArrayList<BakedQuad>(baked.getQuads(null, null, 0));
                    for (EnumFacing side : EnumFacing.values()) quads.addAll(baked.getQuads(null, side, 0));
                    bakedParts.put(location + "@" + turn, Collections.unmodifiableList(quads));
                }
            } catch (Exception exception) {
                com.vandorlabs.VandorLabs.logger.error("Cannot bake track part {}. Affected states will use the solo fallback.", location, exception);
            }
        }
        Map<String, List<BakedQuad>> immutable = Collections.unmodifiableMap(bakedParts);
        for (TrackBlock block : com.vandorlabs.gear.track.TrackRegistry.BLOCKS) {
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                ModelResourceLocation location = new ModelResourceLocation(block.getRegistryName(), "assembled=false,facing=" + facing.getName());
                IBakedModel fallback = event.getModelRegistry().getObject(location);
                if (fallback != null) event.getModelRegistry().putObject(location, new ConnectedModel(fallback, block.style(), facing, immutable));
                else com.vandorlabs.VandorLabs.logger.error("Missing native track blockstate {}", location);
            }
        }
    }

    private static final class ConnectedModel implements IBakedModel {
        private final IBakedModel fallback;
        private final TrackTopology.Style style;
        private final EnumFacing facing;
        private final Map<String, List<BakedQuad>> parts;
        private final ConcurrentHashMap<Integer, List<BakedQuad>> combinations = new ConcurrentHashMap<Integer, List<BakedQuad>>();

        ConnectedModel(IBakedModel fallback, TrackTopology.Style style, EnumFacing facing, Map<String, List<BakedQuad>> parts) {
            this.fallback = fallback; this.style = style; this.facing = facing; this.parts = parts;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if(state!=null&&state.getValue(com.vandorlabs.gear.GearBlock.ASSEMBLED))return Collections.emptyList();
            if (!(state instanceof IExtendedBlockState)) return fallback.getQuads(state, side, rand);
            TrackAppearance appearance = ((IExtendedBlockState) state).getValue(TrackAppearance.PROPERTY);
            if (appearance == null) return fallback.getQuads(state, side, rand);
            // Native parts deliberately have no world cullface. Their topology masks
            // remove internal faces, while transformed host renderers retain visibility.
            if (side != null) return Collections.emptyList();
            Integer selected=((IExtendedBlockState)state).getValue(TrackAppearance.STYLE);TrackTopology.Style chosen=selected==null?style:TrackTopology.Style.values()[selected];int key=appearance.key()|chosen.ordinal()<<18;
            List<BakedQuad> cached = combinations.get(key);
            if (cached != null) return cached;
            List<BakedQuad> quads = new ArrayList<BakedQuad>();
            for (TrackParts.Part part : TrackParts.forCell(chosen, facing, appearance)) {
                List<BakedQuad> nativeQuads = parts.get(part.model + "@" + part.quarterTurns);
                if (nativeQuads == null) return fallback.getQuads(state, side, rand);
                quads.addAll(nativeQuads);
            }
            List<BakedQuad> result = Collections.unmodifiableList(quads);
            // Bound incidental support combinations without enumerating them as blockstates.
            if (combinations.size() >= 512) combinations.clear();
            List<BakedQuad> previous = combinations.putIfAbsent(key, result);
            return previous == null ? result : previous;
        }

        @Override public boolean isAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return fallback.isGui3d(); }
        @Override public boolean isBuiltInRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return fallback.getParticleTexture(); }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return fallback.getItemCameraTransforms(); }
        @Override public ItemOverrideList getOverrides() { return fallback.getOverrides(); }
    }
}
