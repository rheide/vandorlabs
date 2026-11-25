package com.vandorlabs.client.constructors;

import com.vandorlabs.blocks.FrameMachineDesign;
import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Stretches the armored body and mounts the instruments to individual cells. */
final class MaxwellRenderer {
    private static final String BODY = "longburn_drive/maxwell_body.obj";
    private static final String IDLE = "longburn_drive/drive_lights.obj";
    private static final String ACTIVE = "longburn_drive/active/drive_lights.obj";
    private static final String INLET = "vandorlabs:blocks/resource_ports/fluid_in_off";
    private static final String GAUGE = "vandorlabs:blocks/longburn_drive/maxwell_gauge";
    private static final String BAR = "vandorlabs:blocks/longburn_drive/maxwell_gauge_bar";
    private static final int FULL_LIGHT = 0x00F000F0;
    private static final int BARS = 8;

    private MaxwellRenderer() {}

    static void stitch(TextureMap atlas) {
        for (String sprite : new String[]{INLET, GAUGE, BAR}) atlas.registerSprite(new ResourceLocation(sprite));
    }

    static void draw(TileEntityConstructor tile, BufferBuilder buffer, double x, double y, double z, int light) {
        EnumFacing front = tile.facing();
        FrameMachineDesign spec = tile.core().specification();
        CoreLayout shape = tile.layout();
        double widthScale = shape.width / (double) spec.width;
        double depthScale = shape.depth / (double) spec.depth;
        int level = tile.maxwellActiveLevel();
        UniversalConstructorModelCache.getByKey(BODY).emitScaled(buffer, x, y, z, front, spec.width, spec.depth,
                light, 1, widthScale, widthScale, depthScale);
        UniversalConstructorModelCache.getByKey(level == 0 ? IDLE : ACTIVE).emitScaled(buffer, x, y, z, front,
                spec.width, spec.depth, level == 0 ? light : FULL_LIGHT, level == 0 ? .35F : .4F + level * .04F,
                widthScale, widthScale, depthScale);

        // The TESR is based at the rotated model origin; instruments use actual block positions.
        BlockPos offset = tile.renderOriginOffset();
        x -= offset.getX() + tile.getPos().getX();
        y -= offset.getY() + tile.getPos().getY();
        z -= offset.getZ() + tile.getPos().getZ();
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        TextureAtlasSprite inlet = atlas.getAtlasSprite(INLET);
        face(buffer, inlet, tile.maxwellPort(front), front, x, y, z, -.4375, .4375, -.4375, .4375, light, .001);
        EnumFacing left = front.rotateYCCW(), right = front.rotateY();
        face(buffer, inlet, tile.maxwellPort(left), left, x, y, z, -.4375, .4375, -.4375, .4375, light, .001);
        face(buffer, inlet, tile.maxwellPort(right), right, x, y, z, -.4375, .4375, -.4375, .4375, light, .001);
        BlockPos gauge = tile.cell(MaxwellEngine.faceCenter(shape.width) + 1, MaxwellEngine.faceCenter(shape.height), 0);
        face(buffer, atlas.getAtlasSprite(GAUGE), gauge, front, x, y, z, -.4375, .4375, -.4375, .4375, light, .001);
        TextureAtlasSprite bar = atlas.getAtlasSprite(BAR);
        int filled = MaxwellEngine.bars(tile.maxwellFuel(), tile.maxwellCapacity(), BARS);
        for (int i = 0; i < filled; i++) {
            double bottom = (-21 + 6 * i) * .875 / 64;
            face(buffer, bar, gauge, front, x, y, z, -.26, .26, bottom, bottom + 4 * .875 / 64, FULL_LIGHT, .002);
        }
    }

    private static void face(BufferBuilder buffer, TextureAtlasSprite tex, BlockPos at, EnumFacing face,
                             double x, double y, double z, double left, double right, double bottom, double top,
                             int light, double offset) {
        double cx = x + at.getX() + .5 + face.getFrontOffsetX() * (.5 + offset);
        double cy = y + at.getY() + .5;
        double cz = z + at.getZ() + .5 + face.getFrontOffsetZ() * (.5 + offset);
        EnumFacing u = face.rotateYCCW();
        int dx = u.getFrontOffsetX(), dz = u.getFrontOffsetZ();
        vertex(buffer, cx + dx * left, cy + bottom, cz + dz * left, tex.getMinU(), tex.getMaxV(), light);
        vertex(buffer, cx + dx * right, cy + bottom, cz + dz * right, tex.getMaxU(), tex.getMaxV(), light);
        vertex(buffer, cx + dx * right, cy + top, cz + dz * right, tex.getMaxU(), tex.getMinV(), light);
        vertex(buffer, cx + dx * left, cy + top, cz + dz * left, tex.getMinU(), tex.getMinV(), light);
    }

    private static void vertex(BufferBuilder buffer, double x, double y, double z, float u, float v, int light) {
        buffer.pos(x, y, z).color(1F, 1F, 1F, 1F).tex(u, v).lightmap(light & 65535, light >>> 16).endVertex();
    }
}
