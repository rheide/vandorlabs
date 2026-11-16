package com.vandorlabs.neoforge.client;

import com.vandorlabs.ui.DialogStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Forge DialogTabButton presentation with native widget input/narration. Disabled means selected. */
public final class NativeDialogTabButton extends Button {
    public NativeDialogTabButton(int x,int y,int width,int height,Component text,OnPress pressed){super(x,y,width,height,text,pressed,DEFAULT_NARRATION);}
    @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        graphics.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),DialogStyle.tabBody(active,isHovered()));
        graphics.fill(getX(),getY()+getHeight()-2,getX()+getWidth(),getY()+getHeight(),DialogStyle.tabLine(active));
        graphics.drawCenteredString(Minecraft.getInstance().font,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-8)/2,DialogStyle.tabText(active));
    }
}
