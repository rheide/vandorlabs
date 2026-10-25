package com.vandorlabs.ui;

import java.util.Arrays;
import java.util.List;

/** Exact binary control channel/mount editor, shared with the legacy channel dialog. */
public final class MountedControlDialog {
    public final int left,top;
    public static final int WIDTH=240,HEIGHT=168;
    public MountedControlDialog(int width,int height){left=(width-WIDTH)/2;top=(height-HEIGHT)/2;}
    public static final class State {
        public final int height,tilt,direction;
        public State(int height,int tilt,int direction){this.height=height;this.tilt=tilt;this.direction=direction;}
        public State action(int id,int step){return new State(id==9?DialogInput.cycle(height,4,step):height,id==10?DialogInput.cycle(tilt,4,step):tilt,id==11?DialogInput.cycle(direction,4,step):direction);}
    }
    public ProgrammableDialogLayout.Button channels(){return new ProgrammableDialogLayout.Button(0,left+116,top+38,106,18,"");}
    public static String heightLabel(int value){return "Base height: "+(value==0?"Standard":"+"+(value*2)+" px");}
    public static String tiltLabel(int value){return "Tilt: "+(value==0?"Flat":value*15+" deg");}
    public static String directionLabel(int value){return new String[]{"Forward","Right","Backward","Left"}[value];}
    public List<ProgrammableDialogLayout.Button> controls(State state){return Arrays.asList(new ProgrammableDialogLayout.Button(9,left+14,top+66,212,20,heightLabel(state.height)),new ProgrammableDialogLayout.Button(10,left+14,top+90,102,20,tiltLabel(state.tilt)),new ProgrammableDialogLayout.Button(11,left+124,top+90,102,20,directionLabel(state.direction)),new ProgrammableDialogLayout.Button(1,left+20,top+140,200,20,"Done"));}
}
