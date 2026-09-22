package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.tiles.TileEntitySpaceGlass;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TESpaceGlass extends TileEntitySpecialRenderer<TileEntitySpaceGlass> {
    private static final java.util.Map<String,ResourceLocation> GLASS = new java.util.HashMap<>();
    static {
        for (String detail:new String[]{"low","medium","high"}) GLASS.put(detail,new ResourceLocation(
                "vandorlabs", "textures/blocks/space_doors/"+detail+"/glass_tile.png"));
    }
    @Override public void render(TileEntitySpaceGlass tile, double x, double y,
            double z, float partial, int stage, float alpha) {
        if (tile.getWorld() == null) return;
        if (!(tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof com.vandorlabs.blocks.BlockSpaceGlass)) return;
        bindTexture(GLASS.get(((com.vandorlabs.blocks.BlockSpaceGlass)
                tile.getWorld().getBlockState(tile.getPos()).getBlock()).detail()));
        int light = tile.getWorld().getCombinedLight(tile.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light%65536,light/65536);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x,y,z);
        if (tile.getWorld().getBlockState(tile.getPos()).getValue(BlockGlassWall.ROTATED)) {
            GlStateManager.translate(.5,0,.5);
            GlStateManager.rotate(-90,0,1,0);
            GlStateManager.translate(-.5,0,-.5);
        }
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.color(1,1,1,1);
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);
        b.pos(0,0,.5).tex(0,1).endVertex();
        b.pos(1,0,.5).tex(1,1).endVertex();
        b.pos(1,1,.5).tex(1,0).endVertex();
        b.pos(0,1,.5).tex(0,0).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
    }
}
