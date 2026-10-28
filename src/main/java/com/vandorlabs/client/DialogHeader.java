package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Shared branding for block configuration headers. */
public final class DialogHeader {
    private static final ResourceLocation LOGO=new ResourceLocation("vandorlabs","textures/gui/vandor_logo.png");
    private DialogHeader(){}
    public static void logo(int left,int top,int width){
        GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);GlStateManager.disableTexture2D();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();
        Minecraft.getMinecraft().getTextureManager().bindTexture(LOGO);
        GlStateManager.color(1,1,1,1);GlStateManager.enableBlend();
        Gui.drawModalRectWithCustomSizedTexture(left+width-32,top+2,0,0,24,14,24,14);
        GlStateManager.disableBlend();
    }
}
