package com.vandorlabs.ui;

/** Shared programmable dialog panel dimensions in logical pixels; widgets belong to adapters. */
public final class ProgrammableDialogLayout {
    public static final int CONTROLS_WIDTH=154, ROW_HEIGHT=22;
    public final int left,top,width,height,controlsX,listX,listWidth,doneY;
    public ProgrammableDialogLayout(int screenWidth,int screenHeight) {
        this(screenWidth,screenHeight,460,332);
    }
    public ProgrammableDialogLayout(int screenWidth,int screenHeight,int maxWidth,int maxHeight) {
        width=Math.min(maxWidth,screenWidth-8);height=Math.min(maxHeight,screenHeight-8);
        left=(screenWidth-width)/2;top=(screenHeight-height)/2;
        controlsX=left+width-CONTROLS_WIDTH-12;listX=left+12;
        listWidth=controlsX-listX-19;doneY=top+height-26;
    }
    /** Allocated picker rows include its search row; the picker reserves that row itself. */
    public int rows(boolean tabs){return Math.max(2,(height-(tabs?60:38))/ROW_HEIGHT);}
}
