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
        boolean lighting=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_LIGHTING);GlStateManager.disableLighting();
        try{
        GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);GlStateManager.disableTexture2D();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();
        Minecraft.getMinecraft().getTextureManager().bindTexture(LOGO);
        Minecraft.getMinecraft().getTextureManager().getTexture(LOGO).setBlurMipmap(false,false);
        GlStateManager.color(1,1,1,1);GlStateManager.enableBlend();
        int h=com.vandorlabs.ui.DialogStyle.logoHeight(headerHeight),w=com.vandorlabs.ui.DialogStyle.logoWidth(headerHeight);
        Gui.drawModalRectWithCustomSizedTexture(com.vandorlabs.ui.DialogStyle.logoLeft(left,width,headerHeight),com.vandorlabs.ui.DialogStyle.logoTop(top,headerHeight),0,0,w,h,w,h);
        GlStateManager.disableBlend();
        }finally{if(lighting)GlStateManager.enableLighting();}
    }
}
