package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Flat notebook tab. Existing dialogs select a tab by setting enabled=false. */
public final class DialogTabButton extends GuiButton {
    public DialogTabButton(int id,int x,int y,int width,int height,String label){super(id,x,y,width,height,label);}
    @Override public void drawButton(Minecraft mc,int mouseX,int mouseY,float partialTicks){
        if(!visible)return;
        hovered=mouseX>=x&&mouseY>=y&&mouseX<x+width&&mouseY<y+height;
        drawRect(x,y,x+width,y+height,com.vandorlabs.ui.DialogStyle.tabBody(enabled,hovered));
        drawRect(x,y+height-2,x+width,y+height,com.vandorlabs.ui.DialogStyle.tabLine(enabled));
        drawCenteredString(mc.fontRenderer,displayString,x+width/2,y+(height-8)/2,com.vandorlabs.ui.DialogStyle.tabText(enabled));
    }
}
