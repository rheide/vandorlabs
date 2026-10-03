package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
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
        b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
        pane(b,PANE_NEAR,1,1,1,1);
        pane(b,PANE_FAR,1,1,1,1);
        Tessellator.getInstance().draw();
        if (tile.getShade()!=0) {
            GlStateManager.disableTexture2D();
            // Both pane surfaces contribute to the tint. These per-face
            // opacities preserve the apparent shade of the old single face.

            b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
            for(double paneDepth:new double[]{PANE_NEAR,PANE_FAR})
                if(tile.getShade()==1)pane(b,paneDepth,.20F,.85F,.95F,.0513F);
                else pane(b,paneDepth,.10F,.12F,.16F,.1754F);
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

    static void pane(BufferBuilder buffer,double depth,float r,float g,float b,float a) {
        boolean near=depth==PANE_NEAR;
        for(int i=0;i<4;i++) {
            int index=near?(4-i)&3:i;
            double x=index==1 || index==2?1:0,y=index<2?0:1;
            buffer.pos(x,y,depth).color(r,g,b,a).tex(x,1-y)
                    .lightmap((int)OpenGlHelper.lastBrightnessY,(int)OpenGlHelper.lastBrightnessX)
                    .normal(0,0,near?-1:1).endVertex();
        }
    }
}
