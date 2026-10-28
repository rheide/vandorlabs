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
        logo(left,top,width,24);
    }
    public static void logo(int left,int top,int width,int headerHeight){
        GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);GlStateManager.disableTexture2D();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();
        Minecraft.getMinecraft().getTextureManager().bindTexture(LOGO);
        GlStateManager.color(1,1,1,1);GlStateManager.enableBlend();
        int h=Math.min(21,headerHeight-2),w=h*12/7;
        Gui.drawModalRectWithCustomSizedTexture(left+width-w-8,top+(headerHeight-h)/2,0,0,w,h,w,h);
        GlStateManager.disableBlend();
    }
}
