package com.vandorlabs.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Current mounted-control General-page option labels and fixed panel geometry. */
public final class MountedControlDialog {
    public static final int PANEL_WIDTH=280,PANEL_HEIGHT=232,HEIGHT=9,TILT=10,DIRECTION=11,SIZE=12;
    private MountedControlDialog(){}
    public static String heightLabel(int value){return "Base height: "+(value==0?"Standard":"+"+(value*2)+" px");}
    public static String tiltLabel(int value){return "Tilt: "+(value==0?"Flat":value*15+" deg");}
    public static String directionLabel(int value){return new String[]{"Forward","Right","Backward","Left"}[value];}
    public static String sizeLabel(boolean twin,int size){return "Size: "+(twin?(size==0?"Small":"Large"):(size==0?"Compact":"Industrial"));}
    public static DialogControl sizeControl(int left,int top,boolean twin,int size){return new DialogControl(SIZE,left+14,top+64,252,20,sizeLabel(twin,size),false,true);}
    public static List<DialogControl> controls(int left,int top,int height,int tilt,int direction){
        return Collections.unmodifiableList(Arrays.asList(
            new DialogControl(HEIGHT,left+14,top+92,252,20,heightLabel(height),false,true),
            new DialogControl(TILT,left+14,top+116,122,20,tiltLabel(tilt),false,true),
            new DialogControl(DIRECTION,left+144,top+116,122,20,directionLabel(direction),false,true)));
    }
}
