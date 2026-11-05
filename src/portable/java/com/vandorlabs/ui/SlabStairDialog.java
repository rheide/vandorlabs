package com.vandorlabs.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Current slab/stair appearance rows. Changes are immediate; Done only closes the host. */
public final class SlabStairDialog {
    public static final int SIDES=127,RESET_SIDES=128,TREAD=130,ORIENTATION=131;
    private static final String[] FACES={"Down","Up","Front","Back","Left","Right"};
    private SlabStairDialog() { }
    private static final class Control extends DialogControl {
        private Control(int id,int x,int y,int width,int height,String label,boolean tab,boolean enabled){super(id,x,y,width,height,label,tab,enabled);}
    }
    /** Side selection remains available while the six dormant face overrides are disabled. */
    public static List<DialogControl> tabs(int listX,int top,int span,boolean facesEnabled,int target){
        int count=facesEnabled?8:2,width=span/count;List<DialogControl> result=new ArrayList<DialogControl>(count);
        for(int i=0;i<count;i++){
            int face=i-2,id=i==0?ProgrammableBlockDialog.MAIN:i==1?SIDES:ProgrammableBlockDialog.FIRST_FACE+face;
            int selected=i==0?-1:i==1?-2:face;
            result.add(new Control(id,listX+i*width,top+54,i==count-1?span-i*width-2:width-2,18,i==0?"Main":i==1?"Sides":FACES[face],true,target!=selected));
        }
        return Collections.unmodifiableList(result);
    }
    public static DialogControl resetSides(int x,int y,int width,int sideTexture){return new Control(RESET_SIDES,x,y,width,20,"Use default side texture",false,sideTexture>=0);}
    /** Saved 16-pixel steps retain their label while the editor is limited to 1/2/4/8. */
    public static int treadIndex(int tread){return Math.min(3,Integer.numberOfTrailingZeros(tread));}
    public static int treadForIndex(int index){return 1<<index;}
    public static String treadLabel(int tread){return "Step size: "+tread+" px";}
    public static String orientationLabel(int roll){return "Orientation: "+(roll==0?"Normal":"Sideways");}
    /** The existing two-choice editor maps either saved sideways roll back to zero. */
    public static int toggleRoll(int roll){return roll==0?1:0;}
    public static DialogControl orientation(int x,int y,int width,int roll){return new Control(ORIENTATION,x,y,width,20,orientationLabel(roll),false,true);}
}
