package com.vandorlabs.client;

import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.shipsystems.ShipSystemMesh;
import com.vandorlabs.tiles.TileEntityShipSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Authored static machinery: opaque parts first, camera-sorted glass last. */
public final class TEShipSystem extends TileEntitySpecialRenderer<TileEntityShipSystem> {
    private static final class DrawFace {
        CanopyMesh.Face source;
        Vec3d[] v;
        double distance;
    }

    @Override
    public void render(
            TileEntityShipSystem tile,
            double x,
            double y,
            double z,
            float partial,
            int stage,
            float alpha) {
        if (tile.block() == null || !tile.draws()) return;
        com.vandorlabs.shipsystems.MountFrame frame=tile.frame();
        List<DrawFace> opaque = new ArrayList<>(), glass = new ArrayList<>(), emissive = new ArrayList<>();
        Vec3d camera =
                new Vec3d(
                        rendererDispatcher.entityX - tile.getPos().getX(),
                        rendererDispatcher.entityY - tile.getPos().getY(),
                        rendererDispatcher.entityZ - tile.getPos().getZ());
        for (CanopyMesh.Face f : ShipSystemMesh.MODELS.get(tile.model())) {
            DrawFace draw = new DrawFace();
            draw.source = f;
            draw.v = new Vec3d[f.vertices.length];
            Vec3d center = Vec3d.ZERO;
            for (int i = 0; i < draw.v.length; i++) {
                draw.v[i] =
                        frame.point(f.vertices[i]);
                center = center.add(draw.v[i]);
            }
            draw.distance = center.scale(1D / draw.v.length).squareDistanceTo(camera);
            String material = f.baseMaterial;
            boolean glowing = tile.visuallyActive() && (material.equals("cyan") || material.equals("amber") || material.equals("display_on")
                    || material.startsWith("energy_") && !material.endsWith("_off")
                    || material.equals("red_light") || material.equals("green_light") || material.equals("hot")
                    || material.equals("energy") || material.equals("light") || material.equals("display"));
            (material.equals("glass") ? glass : glowing ? emissive : opaque).add(draw);
        }
        glass.sort((a, b) -> Double.compare(b.distance, a.distance));
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.disableLighting();
        GlStateManager.enableCull();
        GlStateManager.color(1, 1, 1, 1);
        float oldX = OpenGlHelper.lastBrightnessX, oldY = OpenGlHelper.lastBrightnessY;
        int light = tile.getWorld().getCombinedLight(tile.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
        draw(opaque);
        float boost=tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld && com.vandorlabs.vehicle.VehicleWorld.isPropulsion(tile.getBlockType())?((com.vandorlabs.vehicle.VehicleWorld)tile.getWorld()).propulsionLevel/15F:1;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (light & 65535)+(240-(light & 65535))*boost, (light >>> 16)+(240-(light >>> 16))*boost);
        draw(emissive);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        draw(glass);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldX, oldY);
        GlStateManager.popMatrix();
    }

    private void draw(List<DrawFace> faces) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        for (DrawFace draw : faces) {
            CanopyMesh.Face f = draw.source;
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite =
                    Minecraft.getMinecraft()
                            .getTextureMapBlocks()
                            .getAtlasSprite("vandorlabs:blocks/ship_systems/" + f.material);
            for (int i = 1; i < f.vertices.length - 1; i++) {
                vertices(b, draw, sprite, new int[] {0, i, i + 1, i + 1});
                if (f.doubleSided) vertices(b, draw, sprite, new int[] {i + 1, i, 0, 0});
            }
        }
        Tessellator.getInstance().draw();
    }

    private void vertices(
            BufferBuilder b,
            DrawFace f,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            int[] indices) {
        Vec3d normal =
                f.v[indices[1]]
                        .subtract(f.v[indices[0]])
                        .crossProduct(f.v[indices[2]].subtract(f.v[indices[0]]))
                        .normalize();
        for (int index : indices) {
            Vec3d p = f.v[index];
            b.pos(p.x, p.y, p.z)
                    .tex(
                            sprite.getInterpolatedU(f.source.uv[index][0] * 16),
                            sprite.getInterpolatedV((1 - f.source.uv[index][1]) * 16))
                    .color(1F, 1F, 1F, 1F)
                    .normal((float) normal.x, (float) normal.y, (float) normal.z)
                    .endVertex();
        }
    }
}
