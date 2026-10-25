package com.vandorlabs.ui;

import java.util.ArrayList;
import java.util.List;

/** Exact draft-until-Done channel editors for binary, power and four-detent controls. */
public final class ControlDialog {
    public enum Profile {
        BINARY(false,false,false,false),TWIN_POWER(false,true,true,false),INDUSTRIAL_POWER(false,true,false,false),THROTTLE(true,false,false,true),WALL_SLIDER(true,false,false,false);
        public final boolean signal,power,twin,selectable;
        Profile(boolean signal,boolean power,boolean twin,boolean selectable){this.signal=signal;this.power=power;this.twin=twin;this.selectable=selectable;}
        public int mountY(){return signal?selectable?146:122:power?96:66;}
        public int height(){return mountY()+102;}
    }
    public static final class State {
        public final int low,high,height,tilt,direction,type,size;
        public State(int low,int high,int height,int tilt,int direction,int type,int size){this.low=low;this.high=high;this.height=height;this.tilt=tilt;this.direction=direction;this.type=type;this.size=size;}
        public State action(int id,int delta){return new State(id==7?DialogInput.cycle(low,1,high-2,delta):low,id==8?DialogInput.cycle(high,low+2,15,delta):high,id==9?DialogInput.cycle(height,4,delta):height,id==10?DialogInput.cycle(tilt,4,delta):tilt,id==11?DialogInput.cycle(direction,4,delta):direction,id==13?DialogInput.cycle(type,3,delta):type,id==12?DialogInput.cycle(size,2,delta):size);}
    }
    public final Profile profile;public final int left,top,width=240,height;
    public ControlDialog(Profile profile,int screenWidth,int screenHeight){this.profile=profile;height=profile.height();left=(screenWidth-width)/2;top=(screenHeight-height)/2;}
    public ProgrammableDialogLayout.Button channels(){return button(0,116,38,106,18,"");}
    private ProgrammableDialogLayout.Button button(int id,int x,int y,int w,int h,String label){return new ProgrammableDialogLayout.Button(id,left+x,top+y,w,h,label);}
    public static String typeLabel(int type){return "Type: "+new String[]{"Standard","Twin","Grip"}[type];}
    public static String sizeLabel(boolean twin,int size){return "Size: "+(twin?(size==0?"Small":"Large"):(size==0?"Compact":"Industrial"));}
    public static String detentLabel(int low,int high){return "Off 0 / Medium "+((low+high+1)/2);}
    public List<ProgrammableDialogLayout.Button> controls(State state){
        List<ProgrammableDialogLayout.Button> result=new ArrayList<>();
        if(profile.signal){result.add(button(7,14,66,102,20,"Low: "+state.low));result.add(button(8,124,66,102,20,"High: "+state.high));}
        if(profile.selectable)result.add(button(13,14,122,212,20,typeLabel(state.type)));
        int y=profile.mountY();result.add(button(9,14,y,212,20,MountedControlDialog.heightLabel(state.height)));result.add(button(10,14,y+24,102,20,MountedControlDialog.tiltLabel(state.tilt)));result.add(button(11,124,y+24,102,20,MountedControlDialog.directionLabel(state.direction)));
        // Legacy insertion order places size after the mount options.
        if(profile.power)result.add(button(12,14,66,212,20,sizeLabel(profile.twin,state.size)));
        result.add(button(1,20,y+74,200,20,"Done"));return result;
    }
}
