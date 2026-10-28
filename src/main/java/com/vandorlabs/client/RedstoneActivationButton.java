package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Shared redstone activation selector; parents retain their saved mode indices and cycling. */
public final class RedstoneActivationButton extends GuiButton {
    public static final int MANUAL=0,ON=1,OFF=2,SIGNAL=3;
    private final String action;
    public RedstoneActivationButton(int id,int x,int y,int width,int mode){this(id,x,y,width,"Activation",mode);}
    public RedstoneActivationButton(int id,int x,int y,int width,String action,int mode){super(id,x,y,width,20,"");this.action=action;setMode(mode);}
    public void setMode(int mode){displayString=action+": "+state(mode);}
    public static RedstoneActivationButton from(GuiButton placement,int mode){return from(placement,"Activation",mode);}
    public static RedstoneActivationButton from(GuiButton placement,String action,int mode){RedstoneActivationButton button=new RedstoneActivationButton(placement.id,placement.x,placement.y,placement.width,action,mode);button.height=placement.height;button.enabled=placement.enabled;button.visible=placement.visible;return button;}
    public static int mode(boolean enabled,boolean inverted){return !enabled?MANUAL:inverted?OFF:ON;}
    public static String state(int mode){return mode==ON?"Redstone ON":mode==OFF?"Redstone OFF":mode==SIGNAL?"Signal level":"Manual";}
    @Override public void drawButton(Minecraft mc,int mouseX,int mouseY,float partial){
        if(!visible)return;
        hovered=mouseX>=x&&mouseY>=y&&mouseX<x+width&&mouseY<y+height;
        drawRect(x,y,x+width,y+height,enabled?(hovered?0xff354958:0xff24323e):0xff202a32);
        int edge=enabled?(hovered?0xffa4d4e8:0xff607c8c):0xff3e505c;
        drawRect(x,y,x+width,y+1,edge);drawRect(x,y+height-1,x+width,y+height,edge);
        drawRect(x,y,x+1,y+height,edge);drawRect(x+width-1,y,x+width,y+height,edge);
        drawCenteredString(mc.fontRenderer,displayString,x+width/2,y+(height-8)/2,enabled?0xffe0e8ec:0xff82939f);
    }
}
