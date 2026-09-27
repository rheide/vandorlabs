package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockTelescopicLandingGear;
import com.vandorlabs.tiles.TileEntityLandingGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.block.state.IBlockState;

/** Applies the three transforms supplied with the telescopic model. */
public final class TELandingGear extends TileEntitySpecialRenderer<TileEntityLandingGear> {
    public void render(TileEntityLandingGear tile,double x,double y,double z,float partial,int stage,float alpha) {
        IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof BlockTelescopicLandingGear) || state.getValue(BlockTelescopicLandingGear.LOWER)) return;
        String name="landing_gear_"+BlockTelescopicLandingGear.SIZES[tile.getSize()];
        ModelResourceLocation FIXED=new ModelResourceLocation("vandorlabs:"+name+"_fixed","inventory");
        ModelResourceLocation WHEEL=new ModelResourceLocation("vandorlabs:"+name+"_wheel","inventory");
        float anchor=BlockTelescopicLandingGear.pistonAnchor(tile.getSize());
        float pistonLength=BlockTelescopicLandingGear.pistonLength(tile.getSize());
        float t=tile.previous+(tile.progress-tile.previous)*partial;
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();GlStateManager.translate(x+.5,y,z+.5);
        GlStateManager.rotate(180-state.getValue(BlockTelescopicLandingGear.FACING).getHorizontalAngle(),0,1,0);
        GlStateManager.translate(-.5,0,-.5);
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
        GlStateManager.disableLighting();GlStateManager.disableCull();
        draw(FIXED);
        GlStateManager.pushMatrix();GlStateManager.translate(0,-t,0);draw(WHEEL);GlStateManager.popMatrix();
        drawArm(anchor, anchor-pistonLength-t);
        GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.popMatrix();
    }
    /** Split the arm at texture repeats; changing length never scales its UVs. */
    private void drawArm(double top, double bottom) {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = Minecraft.getMinecraft()
                .getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(org.lwjgl.opengl.GL11.GL_QUADS,net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
        double low=6/16D,high=10/16D;
        for(double upper=top;upper>bottom+1E-8;upper-=1) {
            double lower=Math.max(bottom,upper-1),v=(upper-lower)*16;
            armQuad(b,sprite, low,upper,low, high,upper,low, high,lower,low, low,lower,low,6,0,10,v);
            armQuad(b,sprite, high,upper,high, low,upper,high, low,lower,high, high,lower,high,6,0,10,v);
            armQuad(b,sprite, low,upper,high, low,upper,low, low,lower,low, low,lower,high,6,0,10,v);
            armQuad(b,sprite, high,upper,low, high,upper,high, high,lower,high, high,lower,low,6,0,10,v);
        }
        armQuad(b,sprite, low,top,high, high,top,high, high,top,low, low,top,low,6,6,10,10);
        armQuad(b,sprite, low,bottom,low, high,bottom,low, high,bottom,high, low,bottom,high,6,6,10,10);
        Tessellator.getInstance().draw();
    }
    private static void armQuad(BufferBuilder b,net.minecraft.client.renderer.texture.TextureAtlasSprite s,
            double x0,double y0,double z0,double x1,double y1,double z1,
            double x2,double y2,double z2,double x3,double y3,double z3,
            double u0,double v0,double u1,double v1) {
        b.pos(x0,y0,z0).tex(s.getInterpolatedU(u0),s.getInterpolatedV(v0)).endVertex();
        b.pos(x1,y1,z1).tex(s.getInterpolatedU(u1),s.getInterpolatedV(v0)).endVertex();
        b.pos(x2,y2,z2).tex(s.getInterpolatedU(u1),s.getInterpolatedV(v1)).endVertex();
        b.pos(x3,y3,z3).tex(s.getInterpolatedU(u0),s.getInterpolatedV(v1)).endVertex();
    }
    private void draw(ModelResourceLocation model) {
        Minecraft mc=Minecraft.getMinecraft();
        mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightnessColor(
                mc.getRenderItem().getItemModelMesher().getModelManager().getModel(model),1,1,1,1);
    }
}
