package com.vandorlabs.neoforge.client;

import com.vandorlabs.ui.DialogStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Version adapter for Source-authored panel/header/logo geometry; never invents control positions. */
public final class NativeDialogVisuals {
    private static final ResourceLocation LOGO=ResourceLocation.parse(DialogStyle.LOGO);
    public static void frame(GuiGraphics graphics,int left,int top,int width,int height,DialogStyle.Shell style){
        graphics.fill(left,top,left+width,top+height,style.body);
        if(style.headerHeight>0)graphics.fill(left,top,left+width,top+style.headerHeight,style.header);
        logo(graphics,left,top,width,style.logoHeaderHeight);
    }
    public static void panel(GuiGraphics graphics,Font font,Component title,int left,int top,int width,int height,DialogStyle.Shell style){
        frame(graphics,left,top,width,height,style);graphics.drawString(font,title,left+style.titleX,top+style.titleY,0xffffffff,false);
    }
    public static void logo(GuiGraphics graphics,int left,int top,int width,int headerHeight){
        int h=DialogStyle.logoHeight(headerHeight),w=DialogStyle.logoWidth(headerHeight);
        if(h>0&&w>0)graphics.blit(LOGO,DialogStyle.logoLeft(left,width,headerHeight),DialogStyle.logoTop(top,headerHeight),0,0,w,h,w,h);
    }
    /** Exact authored Light slider, distinct from the Ramp's Forge GuiSlider skin. */
    public static void lightSlider(GuiGraphics graphics,int left,int top,int width,int selected,int maximum){
        graphics.fill(left,top+4,left+width,top+10,DialogStyle.LIGHT_SLIDER_TRACK);
        int thumb=left+Math.round(width*Math.max(0,Math.min(maximum,selected))/(float)Math.max(1,maximum));
        graphics.fill(thumb-3,top,thumb+3,top+14,DialogStyle.LIGHT_SLIDER_THUMB);
    }
    private NativeDialogVisuals(){}
}
