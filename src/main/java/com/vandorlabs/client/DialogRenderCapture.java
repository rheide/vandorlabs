package com.vandorlabs.client;

import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;
import org.lwjgl.opengl.GL11;

/** Render a test dialog at its declared logical resolution, independent of the game window. */
final class DialogRenderCapture {
    static BufferedImage image(Minecraft mc,GuiScreen gui,int width,int height){
        Framebuffer frame=new Framebuffer(width,height,true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,width,height,0,-1000,1000);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(0,0,0,1);GlStateManager.clear(16640);
            GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.disableDepth();GlStateManager.disableCull();GlStateManager.disableBlend();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();
            gui.drawScreen(-1,-1,0);GL11.glFinish();return ScreenShotHelper.createScreenshot(width,height,frame);
        }finally{
            GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
        }
    }
    private DialogRenderCapture(){}
}
