package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.shipsystems.ShipSystemMesh;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.lwjgl.util.vector.Vector3f;

import java.util.*;

public final class ShipSystemModels {
    @SubscribeEvent
    public void register(ModelRegistryEvent event) {
        for (net.minecraft.block.Block b : ModBlocks.BLOCKS)
            if (b instanceof BlockShipSystem)
                net.minecraftforge.client.model.ModelLoader.setCustomStateMapper(
                        b,
                        new net.minecraft.client.renderer.block.statemap.StateMap.Builder()
                                .ignore(BlockShipSystem.FACING)
                                .build());
    }

    @SubscribeEvent
    public void textures(TextureStitchEvent.Pre event) {
        for (String m : ShipSystemMesh.MATERIALS)
            event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/ship_systems/" + m));
    }

    @SubscribeEvent
    public void bake(ModelBakeEvent event) {
        for (net.minecraft.block.Block b : ModBlocks.BLOCKS)
            if (b instanceof BlockShipSystem) {
                String id = ((BlockShipSystem) b).itemModel();
                event.getModelRegistry()
                        .putObject(
                                new ModelResourceLocation(b.getRegistryName(), "inventory"),
                                new Icon(
                                        id));
            }
    }

    public static final class Icon implements IBakedModel {
        private final List<BakedQuad> quads = new ArrayList<>();
        private final TextureAtlasSprite particle;
        private final ItemCameraTransforms transforms;
        private final double itemScale;
        private final double centerX, centerY, centerZ;

        Icon(String id) {
            int[] size = ShipSystemMesh.DIMENSIONS.get(id);
            itemScale = 1D / Math.max(size[0], Math.max(size[1], size[2]));
            centerX = size[0] / 2D;
            centerY = size[2] / 2D;
            centerZ = size[1] / 2D;
            for (CanopyMesh.Face f : ShipSystemMesh.MODELS.get(id))
                for (int triangle = 1; triangle < f.vertices.length - 1; triangle++) {
                    add(f, new int[] {0, triangle, triangle + 1, triangle + 1});
                    if (f.doubleSided) add(f, new int[] {triangle + 1, triangle, 0, 0});
                }
            particle =
                    Minecraft.getMinecraft()
                            .getTextureMapBlocks()
                            .getAtlasSprite("vandorlabs:blocks/ship_systems/" + ShipSystemMesh.MODELS.get(id).get(0).material);
            ItemTransformVec3f gui =
                    new ItemTransformVec3f(
                            new Vector3f(25, 225, 0), new Vector3f(0, 0, 0), new Vector3f(1, 1, 1));
            ItemCameraTransforms source =
                    new ItemCameraTransforms(
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            gui,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT);
            transforms = ControlItemPadding.fit(this, source);
        }

        private void add(CanopyMesh.Face f, int[] index) {
            int[] data = new int[28];
            TextureAtlasSprite sprite =
                    Minecraft.getMinecraft()
                            .getTextureMapBlocks()
                            .getAtlasSprite("vandorlabs:blocks/ship_systems/" + f.material);
            Vec3d normal =
                    f.vertices[index[1]]
                            .subtract(f.vertices[index[0]])
                            .crossProduct(f.vertices[index[2]].subtract(f.vertices[index[0]]))
                            .normalize();
            for (int i = 0; i < 4; i++) {
                int v = index[i], offset = i * 7;
                Vec3d p = f.vertices[v];
                data[offset] = Float.floatToRawIntBits((float) (.5 + (p.x - centerX) * itemScale));
                data[offset + 1] = Float.floatToRawIntBits((float) (.5 + (p.y - centerY) * itemScale));
                data[offset + 2] = Float.floatToRawIntBits((float) (.5 + (p.z - centerZ) * itemScale));
                data[offset + 3] = 0xffffffff;
                data[offset + 4] =
                        Float.floatToRawIntBits(sprite.getInterpolatedU(f.uv[v][0] * 16));
                data[offset + 5] =
                        Float.floatToRawIntBits(sprite.getInterpolatedV((1 - f.uv[v][1]) * 16));
                data[offset + 6] =
                        ((int) (normal.x * 127) & 255)
                                | (((int) (normal.y * 127) & 255) << 8)
                                | (((int) (normal.z * 127) & 255) << 16);
            }
            quads.add(
                    new BakedQuad(
                            data,
                            -1,
                            EnumFacing.getFacingFromVector(
                                    (float) normal.x, (float) normal.y, (float) normal.z),
                            sprite,
                            true,
                            DefaultVertexFormats.ITEM));
        }

        public List<BakedQuad> getQuads(IBlockState s, EnumFacing side, long seed) {
            return side == null ? quads : Collections.emptyList();
        }

        public boolean isAmbientOcclusion() {
            return false;
        }

        public boolean isGui3d() {
            return true;
        }

        public boolean isBuiltInRenderer() {
            return false;
        }

        public TextureAtlasSprite getParticleTexture() {
            return particle;
        }

        public ItemCameraTransforms getItemCameraTransforms() {
            return transforms;
        }

        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }
    }
}
