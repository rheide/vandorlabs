package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockTurntable;
import com.vandorlabs.tiles.TileEntityTurntable;
import com.vandorlabs.turntable.TurntableGeometry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Spins a small textured marker on the rotating face of a Turntable platform
 * block, using the same angle and axis as the captured payload so the platform
 * visibly turns even when nothing sits on it.
 */
@SideOnly(Side.CLIENT)
public class TETurntable extends TileEntitySpecialRenderer<TileEntityTurntable> {
    private static final String MARKER = "vandorlabs:block/turntable_indicator";
    private static final double RADIUS = 0.3;
    private static final double OFFSET = 0.002;

    @Override public void render(TileEntityTurntable te, double x, double y, double z,
            float partial, int destroyStage, float alpha) {
        if (!te.active || te.getWorld() == null) return;
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockTurntable)) return;
        EnumFacing facing = state.getValue(BlockTurntable.FACING);
        double angle = te.angle(partial);
        int axis = te.axis;
        if (!TurntableGeometry.validAxis(axis)) axis = facing.getAxis() == EnumFacing.Axis.X
                ? TurntableGeometry.AXIS_X : facing.getAxis() == EnumFacing.Axis.Z
                ? TurntableGeometry.AXIS_Z : TurntableGeometry.AXIS_Y;

        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(MARKER);
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.color(1, 1, 1, 1);
        emit(facing, axis, angle, sprite);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** Emits the marking quad in the face plane, turned about the platform axis. */
    private static void emit(EnumFacing facing, int axis, double angle, TextureAtlasSprite sprite) {
        double[][] corners;
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            double yy = facing == EnumFacing.UP ? 1 + OFFSET : -OFFSET;
            corners = new double[][] { { -RADIUS, yy, -RADIUS }, { RADIUS, yy, -RADIUS },
                    { RADIUS, yy, RADIUS }, { -RADIUS, yy, RADIUS } };
        } else if (facing.getAxis() == EnumFacing.Axis.Z) {
            double zz = facing == EnumFacing.SOUTH ? 1 + OFFSET : -OFFSET;
            corners = new double[][] { { -RADIUS, -RADIUS, zz }, { RADIUS, -RADIUS, zz },
                    { RADIUS, RADIUS, zz }, { -RADIUS, RADIUS, zz } };
        } else {
            double xx = facing == EnumFacing.EAST ? 1 + OFFSET : -OFFSET;
            corners = new double[][] { { xx, -RADIUS, -RADIUS }, { xx, -RADIUS, RADIUS },
                    { xx, RADIUS, RADIUS }, { xx, RADIUS, -RADIUS } };
        }
        float[] uv = { sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMinV(),
                sprite.getMaxU(), sprite.getMaxV(), sprite.getMinU(), sprite.getMaxV() };
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        for (int i = 0; i < corners.length; i++) {
            double[] spun = TurntableGeometry.rotate(corners[i][0], corners[i][1], corners[i][2], axis, angle);
            buffer.pos(0.5 + spun[0], 0.5 + spun[1], 0.5 + spun[2]).tex(uv[i * 2], uv[i * 2 + 1]).endVertex();
        }
        Tessellator.getInstance().draw();
    }
}
