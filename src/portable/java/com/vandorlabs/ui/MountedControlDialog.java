package com.vandorlabs.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Current mounted-control General-page option labels and fixed panel geometry. */
public final class MountedControlDialog {
    public static final int PANEL_WIDTH=280,PANEL_HEIGHT=232,HEIGHT=9,TILT=10,DIRECTION=11,SIZE=12,TYPE=13,LOW=7,HIGH=8;
    public static final int TYPE_COUNT=3,LOW_MINIMUM=1,HIGH_MAXIMUM=15,LIMIT_LABEL_Y=160,LIMIT_HINT_Y=174;
    public static final String LIMIT_HINT="Click to cycle four levels";
    private MountedControlDialog(){}
    public static String heightLabel(int value){return "Base height: "+(value==0?"Standard":"+"+(value*2)+" px");}
    public static String tiltLabel(int value){return "Tilt: "+(value==0?"Flat":value*15+" deg");}
    public static String directionLabel(int value){return new String[]{"Forward","Right","Backward","Left"}[value];}
    public static String sizeLabel(boolean twin,int size){return "Size: "+(twin?(size==0?"Small":"Large"):(size==0?"Compact":"Industrial"));}
    public static DialogControl sizeControl(int left,int top,boolean twin,int size){return new DialogControl(SIZE,left+14,top+64,252,20,sizeLabel(twin,size),false,true);}
    public static String typeLabel(int type){return "Type: "+new String[]{"Standard","Twin","Grip"}[type];}
    public static String lowLabel(int low){return "Low: "+low;}
    public static String highLabel(int high){return "High: "+high;}
    public static String detentLabel(int low,int high){return "Off 0 / Medium "+com.vandorlabs.persistence.SignalControlData.levelForStep(2,low,high);}
    public static int lowMaximum(int high){return high-2;}
    public static int highMinimum(int low){return low+2;}
    public static int cycleLow(int low,int high,int direction){return LOW_MINIMUM+DialogInput.cycle(low-LOW_MINIMUM,lowMaximum(high)-LOW_MINIMUM+1,direction);}
    public static int cycleHigh(int low,int high,int direction){int minimum=highMinimum(low);return minimum+DialogInput.cycle(high-minimum,HIGH_MAXIMUM-minimum+1,direction);}
    public static DialogControl typeControl(int left,int top,int type){return new DialogControl(TYPE,left+14,top+64,252,20,typeLabel(type),false,true);}
    public static List<DialogControl> limitControls(int left,int top,int low,int high){return Collections.unmodifiableList(Arrays.asList(new DialogControl(LOW,left+14,top+132,122,20,lowLabel(low),false,true),new DialogControl(HIGH,left+144,top+132,122,20,highLabel(high),false,true)));}
    public static List<DialogControl> controls(int left,int top,int height,int tilt,int direction){
        return Collections.unmodifiableList(Arrays.asList(
            new DialogControl(HEIGHT,left+14,top+92,252,20,heightLabel(height),false,true),
            new DialogControl(TILT,left+14,top+116,122,20,tiltLabel(tilt),false,true),
            new DialogControl(DIRECTION,left+144,top+116,122,20,directionLabel(direction),false,true)));
    }
}
