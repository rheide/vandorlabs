package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TEProgrammableGlass extends TileEntitySpecialRenderer<TileEntityProgrammableGlass> {
    private static final double PANE_NEAR = 7D / 16D;
    private static final double PANE_FAR = 9D / 16D;
    private static final java.util.Map<String,ResourceLocation> GLASS = new java.util.HashMap<>();
    static {
        for (String detail:new String[]{"low","medium","high"}) GLASS.put(detail,new ResourceLocation(
                "vandorlabs", "textures/blocks/space_doors/"+detail+"/glass_tile.png"));
    }
    @Override public void render(TileEntityProgrammableGlass tile, double x, double y,
            double z, float partial, int stage, float alpha) {
        if (tile.getWorld() == null) return;
        if (!(tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof com.vandorlabs.blocks.BlockProgrammableGlass)) return;
        int size = tile.getSize();
        bindTexture(GLASS.get(new String[]{"low", "medium", "high"}[size]));
        int light = tile.getWorld().getCombinedLight(tile.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light%65536,light/65536);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x,y,z);
        if (tile.getWorld().getBlockState(tile.getPos()).getValue(BlockGlassWall.ROTATED)) {
            GlStateManager.translate(.5,0,.5);
            GlStateManager.rotate(-90,0,1,0);
            GlStateManager.translate(-.5,0,-.5);
        }
        int depth = tile.getWorld().getBlockState(tile.getPos()).getValue(BlockGlassWall.DEPTH);
        GlStateManager.translate(0, 0, com.vandorlabs.blocks.PanelDepth.offset(depth) / 16D);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.color(1F, 1F, 1F, 1F);
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);
        texturedFace(b, PANE_NEAR);
        texturedFace(b, PANE_FAR);
        Tessellator.getInstance().draw();
        if (tile.getShade()!=0) {
            GlStateManager.disableTexture2D();
            // Both pane surfaces contribute to the tint. These per-face
            // opacities preserve the apparent shade of the old single face.
            if (tile.getShade()==1) GlStateManager.color(.20F,.85F,.95F,.0513F);
            else GlStateManager.color(.10F,.12F,.16F,.1754F);
            b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION);
            tintedFace(b, PANE_NEAR);
            tintedFace(b, PANE_FAR);
            Tessellator.getInstance().draw();
            GlStateManager.enableTexture2D();
        }
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.popMatrix();
        bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    private static void texturedFace(BufferBuilder b, double depth) {
        b.pos(0,0,depth).tex(0,1).endVertex();
        b.pos(1,0,depth).tex(1,1).endVertex();
        b.pos(1,1,depth).tex(1,0).endVertex();
        b.pos(0,1,depth).tex(0,0).endVertex();
    }

    private static void tintedFace(BufferBuilder b, double depth) {
        b.pos(0,0,depth).endVertex();
        b.pos(1,0,depth).endVertex();
        b.pos(1,1,depth).endVertex();
        b.pos(0,1,depth).endVertex();
    }
}
