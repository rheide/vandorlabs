package com.vandorlabs.neoforge.vehicle.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.vandorlabs.neoforge.vehicle.NativeCruiseTerrain;
import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;

/** A world-space terrain projection drawn after the sky and before the vehicle and level meshes. */
@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeCruiseSky {
    private static final ResourceLocation IMAGE = ResourceLocation.parse("vandorlabs:cruise_terrain");
    private static final float[] GRID = {0, .12F, .88F, 1};
    private static NativeCruiseTerrain terrain;
    private static NativeGroundVehicle owner;
    private static DynamicTexture texture;
    private static boolean emitted;

    static {
        NativeCruiseTerrain.receiver = NativeCruiseSky::receive;
    }

    static boolean ready(NativeGroundVehicle craft) {
        return owner == craft && emitted;
    }

    private static void receive(NativeCruiseTerrain picture) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof NativeGroundVehicle craft)
                || craft.getId() != picture.vehicle()) return;
        if (texture == null) {
            texture = new DynamicTexture(NativeCruiseTerrain.SIZE, NativeCruiseTerrain.SIZE, false);
            texture.setFilter(true, false);
            mc.getTextureManager().register(IMAGE, texture);
        }
        terrain = picture;
        owner = craft;
        var image = texture.getPixels();
        for (int z = 0; z < NativeCruiseTerrain.SIZE; z++) {
            for (int x = 0; x < NativeCruiseTerrain.SIZE; x++) {
                int rgb = picture.pixels()[z * NativeCruiseTerrain.SIZE + x];
                image.setPixelRGBA(x, z, 0xff000000 | (rgb & 255) << 16 | rgb & 0xff00 | rgb >>> 16 & 255);
            }
        }
        texture.upload();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (owner == null) return;
        var player = Minecraft.getInstance().player;
        if (player == null || player.getVehicle() != owner || !owner.highFlight()) clear();
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    private static void clear() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(IMAGE);
        terrain = null;
        owner = null;
        emitted = false;
        texture = null;
    }

    @SubscribeEvent public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || terrain == null || owner == null
                || !owner.highFlight() || event.getCamera().getEntity() == null
                || event.getCamera().getEntity().getVehicle() != owner) return;

        var mc = Minecraft.getInstance();
        float width = Math.max(40, mc.options.getEffectiveRenderDistance() * 12);
        double scale = width / (NativeCruiseTerrain.SIZE * (double)terrain.step());
        if (Math.abs(owner.routeX() - terrain.x()) * scale >= width / 3
                || Math.abs(owner.routeZ() - terrain.z()) * scale >= width / 3) return;
        var view = event.getCamera().getPosition();
        float x = (float)((terrain.x() - owner.routeX()) * scale - width / 2 + owner.getX() - view.x);
        float z = (float)((terrain.z() - owner.routeZ()) * scale - width / 2 + owner.getZ() - view.z);
        float y = (float)(-Math.max(5, owner.flightAltitude() * scale) + owner.getY() - view.y);
        var matrix = event.getModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, IMAGE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        try {
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    point(buffer, matrix, x, y, z, width, column, row);
                    point(buffer, matrix, x, y, z, width, column, row + 1);
                    point(buffer, matrix, x, y, z, width, column + 1, row + 1);
                    point(buffer, matrix, x, y, z, width, column + 1, row);
                }
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            emitted = true;
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void point(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float width, int col, int row) {
        float u = GRID[col], v = GRID[row];
        int alpha = col == 0 || row == 0 || col == 3 || row == 3 ? 0 : 255;
        buffer.addVertex(matrix, x + u * width, y, z + v * width).setUv(u, v).setColor(255, 255, 255, alpha);
    }

    private NativeCruiseSky() {}
}
