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
        ModelResourceLocation PISTON=new ModelResourceLocation("vandorlabs:"+name+"_piston","inventory");
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
        GlStateManager.pushMatrix();GlStateManager.translate(0,anchor,0);GlStateManager.scale(1,1+t/pistonLength,1);
        GlStateManager.translate(0,-anchor,0);draw(PISTON);GlStateManager.popMatrix();
        GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.popMatrix();
    }
    private void draw(ModelResourceLocation model) {
        Minecraft mc=Minecraft.getMinecraft();
        mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightnessColor(
                mc.getRenderItem().getItemModelMesher().getModelManager().getModel(model),1,1,1,1);
    }
}
