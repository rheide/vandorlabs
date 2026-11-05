package com.vandorlabs.ui;

/** Current cube dialog dimensions in logical GUI pixels; widgets belong to adapters. */
public final class ProgrammableDialogLayout {
    public static final int CONTROLS_WIDTH=154, ROW_HEIGHT=22;
    public final int left,top,width,height,controlsX,listX,listWidth,doneY;
    public ProgrammableDialogLayout(int screenWidth,int screenHeight) {
        width=Math.min(460,screenWidth-8);height=Math.min(332,screenHeight-8);
        left=(screenWidth-width)/2;top=(screenHeight-height)/2;
        controlsX=left+width-CONTROLS_WIDTH-12;listX=left+12;
        listWidth=controlsX-listX-19;doneY=top+height-26;
    }
}
