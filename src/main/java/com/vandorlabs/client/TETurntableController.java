package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.turntable.TurntableGeometry;
import com.vandorlabs.turntable.TurntableSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Draws the captured volume turning about the platform axis. The payload is
 * removed from the world while the turntable runs, so this renderer redraws the
 * stored block states at their fractional angle each frame.
 */
@SideOnly(Side.CLIENT)
public class TETurntableController extends TileEntitySpecialRenderer<TileEntityTurntableController> {
    @Override public void render(TileEntityTurntableController te, double x, double y, double z,
            float partial, int destroyStage, float alpha) {
        TurntableSnapshot payload = te.clientPayload();
        if (payload == null || payload.isEmpty() || te.getWorld() == null) return;
        World world = te.getWorld();
        Minecraft minecraft = Minecraft.getMinecraft();
        double angle = te.angle(partial);

        GlStateManager.pushMatrix();
        // renderBlock writes absolute world coordinates, so undo the camera
        // offset the dispatcher baked into x/y/z, then turn about the pivot.
        GlStateManager.translate(x - te.getPos().getX(), y - te.getPos().getY(), z - te.getPos().getZ());
        double pivotX = te.pivotX(), pivotY = te.pivotY(), pivotZ = te.pivotZ();
        GlStateManager.translate(pivotX, pivotY, pivotZ);
        switch (te.axis()) {
            case TurntableGeometry.AXIS_X: GlStateManager.rotate((float) angle, 1, 0, 0); break;
            case TurntableGeometry.AXIS_Z: GlStateManager.rotate((float) angle, 0, 0, 1); break;
            default: GlStateManager.rotate((float) angle, 0, 1, 0); break;
        }
        GlStateManager.translate(-pivotX, -pivotY, -pivotZ);
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        // A TESR runs outside the chunk renderer, so the payload is submitted
        // once per render layer just like the baked vehicle meshes.
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (!hasLayer(payload, world, layer)) continue;
            ForgeHooksClient.setRenderLayer(layer);
            try {
                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                for (TurntableSnapshot.Cell cell : payload.cells) {
                    if (cell.state.getBlock().isAir(cell.state, world, cell.pos)) continue;
                    if (!cell.state.getBlock().canRenderInLayer(cell.state, layer)) continue;
                    try {
                        minecraft.getBlockRendererDispatcher().renderBlock(cell.state, cell.pos, world, buffer);
                    } catch (RuntimeException | LinkageError failure) {
                        // A single incompatible model must not abort the whole turntable frame.
                        com.vandorlabs.vehicle.VehicleCompatibility.warn(cell.state.getBlock(),
                                "turntable payload render", failure);
                    }
                }
                Tessellator.getInstance().draw();
            } finally {
                ForgeHooksClient.setRenderLayer(null);
            }
        }
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static boolean hasLayer(TurntableSnapshot payload, World world, BlockRenderLayer layer) {
        for (TurntableSnapshot.Cell cell : payload.cells) {
            if (cell.state.getBlock().isAir(cell.state, world, cell.pos)) continue;
            if (cell.state.getBlock().canRenderInLayer(cell.state, layer)) return true;
        }
        return false;
    }
}
