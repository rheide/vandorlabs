package com.vandorlabs.ui;

/** Logical pixels shared by platform widgets; adapters measure and trim labels. */
public class ProgrammableDialogLayout {
    public static final int CONTROLS_WIDTH=154,ROW_HEIGHT=22;
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
    public int rows(boolean tabs){return Math.max(2,(height-(tabs?60:38))/ROW_HEIGHT);}
    public Button controlBounds(int id,int y,String label){return new Button(id,controlsX,top+y,CONTROLS_WIDTH,20,label);}
    public Button doneBounds(int id){return new Button(id,controlsX,doneY,CONTROLS_WIDTH,20,"Done");}
    public Button tabBounds(int id,int index,int count,String label){int w=(listWidth+2)/count;return new Button(id,listX+index*w,top+30,w-2,18,label);}
    public Button choiceBounds(int id,int index,int count,int y,String label){int w=(CONTROLS_WIDTH+2)/count;return new Button(id,controlsX+index*w,top+y,w-2,18,label);}
    public static final class Button {
        public final int id,x,y,width,height;
        public final String label;
        public Button(int id,int x,int y,int width,int height,String label){this.id=id;this.x=x;this.y=y;this.width=width;this.height=height;this.label=label;}
        public boolean contains(double mouseX,double mouseY){return mouseX>=x&&mouseX<x+width&&mouseY>=y&&mouseY<y+height;}
    }
}
