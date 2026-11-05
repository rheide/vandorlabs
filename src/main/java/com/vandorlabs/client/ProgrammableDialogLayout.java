package com.vandorlabs.client;

import net.minecraft.client.gui.GuiButton;

/** Shared dimensions for a tall texture list and a compact settings column. */
final class ProgrammableDialogLayout {
    static final int CONTROLS_WIDTH=com.vandorlabs.ui.ProgrammableDialogLayout.CONTROLS_WIDTH;
    final int left,top,width,height,controlsX,listX,listWidth,doneY;
    private final com.vandorlabs.ui.ProgrammableDialogLayout shared;
    ProgrammableDialogLayout(int screenWidth,int screenHeight) {
        this(screenWidth,screenHeight,460,332);
    }
    ProgrammableDialogLayout(int screenWidth,int screenHeight,int maxWidth,int maxHeight) {
        shared=new com.vandorlabs.ui.ProgrammableDialogLayout(screenWidth,screenHeight,maxWidth,maxHeight);
        left=shared.left;top=shared.top;width=shared.width;height=shared.height;
        controlsX=shared.controlsX;listX=shared.listX;listWidth=shared.listWidth;doneY=shared.doneY;
    }
    int rows(boolean tabs){return shared.rows(tabs);}
    GuiButton control(int id,int y,String label){return new GuiButton(id,controlsX,top+y,CONTROLS_WIDTH,20,label);}
    GuiButton done(int id){return new GuiButton(id,controlsX,doneY,CONTROLS_WIDTH,20,"Done");}
    GuiButton tab(int id,int index,int count,String label){int w=(listWidth+2)/count;return new DialogTabButton(id,listX+index*w,top+30,w-2,18,net.minecraft.client.Minecraft.getMinecraft().fontRenderer.trimStringToWidth(label,w-6));}
    GuiButton fullTab(int id,int index,int count,String label){int w=(width-22)/count;return new DialogTabButton(id,left+12+index*w,top+30,w-2,18,label);}
    GuiButton choice(int id,int index,int count,int y,String label){int w=(CONTROLS_WIDTH+2)/count;return new GuiButton(id,controlsX+index*w,top+y,w-2,18,label);}
}
