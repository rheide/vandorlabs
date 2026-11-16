package com.vandorlabs.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Forge GuiButton artwork, half-width cropping and text colors, with native input/narration. */
public class NativeDialogButton extends Button {
    private static final ResourceLocation ART=ResourceLocation.parse("vandorlabs:textures/gui/source_buttons.png");
    public NativeDialogButton(int x,int y,int width,int height,Component title,OnPress press){this(x,y,width,height,title,press,DEFAULT_NARRATION);}
    private NativeDialogButton(int x,int y,int width,int height,Component title,OnPress press,CreateNarration narration){super(x,y,width,height,title,press,narration);}
    @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partial){int row=!active?0:isHovered()?40:20,half=getWidth()/2;
        graphics.blit(ART,getX(),getY(),0,row,half,getHeight(),200,60);graphics.blit(ART,getX()+half,getY(),200-half,row,half,getHeight(),200,60);
        graphics.drawCenteredString(Minecraft.getInstance().font,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-8)/2,!active?0xa0a0a0:isHovered()?0xffffa0:0xe0e0e0);
    }
    public static Builder builder(Component title,OnPress press){return new Builder(title,press);}
    public static final class Builder extends Button.Builder {
        private final Component title;private final OnPress press;private int x,y,width=150,height=20;private Tooltip tooltip;private CreateNarration narration=DEFAULT_NARRATION;
        private Builder(Component title,OnPress press){super(title,press);this.title=title;this.press=press;}
        @Override public Builder pos(int x,int y){this.x=x;this.y=y;return this;}
        @Override public Builder width(int width){this.width=width;return this;}
        @Override public Builder size(int width,int height){this.width=width;this.height=height;return this;}
        @Override public Builder bounds(int x,int y,int width,int height){return pos(x,y).size(width,height);}
        @Override public Builder tooltip(Tooltip tooltip){this.tooltip=tooltip;return this;}
        @Override public Builder createNarration(CreateNarration narration){this.narration=narration;return this;}
        @Override public Button build(){var button=new NativeDialogButton(x,y,width,height,title,press,narration);button.setTooltip(tooltip);return button;}
    }
}
