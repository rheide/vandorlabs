package com.vandorlabs.client;

import net.minecraft.client.gui.GuiButton;

/** Shared dimensions for a tall texture list and a compact settings column. */
final class ProgrammableDialogLayout {
    static final int CONTROLS_WIDTH=154;
    final int left,top,width,height,controlsX,listX,listWidth,doneY;
    ProgrammableDialogLayout(int screenWidth,int screenHeight) {
        width=Math.min(460,screenWidth-8);height=Math.min(332,screenHeight-8);
        left=(screenWidth-width)/2;top=(screenHeight-height)/2;
        controlsX=left+width-CONTROLS_WIDTH-12;listX=left+12;
        listWidth=controlsX-listX-19;doneY=top+height-26;
    }
    int rows(boolean tabs){return Math.max(2,(height-(tabs?60:38))/HousingTextureList.ROW_HEIGHT);}
    GuiButton control(int id,int y,String label){return new GuiButton(id,controlsX,top+y,CONTROLS_WIDTH,20,label);}
    GuiButton done(int id){return new GuiButton(id,controlsX,doneY,CONTROLS_WIDTH,20,"Done");}
    GuiButton tab(int id,int index,int count,String label){int w=(listWidth+2)/count;return new GuiButton(id,listX+index*w,top+30,w-2,18,net.minecraft.client.Minecraft.getMinecraft().fontRenderer.trimStringToWidth(label,w-6));}
    GuiButton choice(int id,int index,int count,int y,String label){int w=(CONTROLS_WIDTH+2)/count;return new GuiButton(id,controlsX+index*w,top+y,w-2,18,label);}
}
