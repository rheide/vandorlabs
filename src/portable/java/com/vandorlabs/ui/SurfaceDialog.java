package com.vandorlabs.ui;

import com.vandorlabs.render.TextureLayoutMode;
import java.util.*;

/** Source-authored Surface artwork editor; IDs and option order remain stable. */
public final class SurfaceDialog {
    public static final String TITLE="Programmable Surface";
    public static final int LAYOUT=1,DONE=2,CUSTOM=3,ACTIVE=90,INACTIVE=91;
    public static final DialogStyle.Shell STYLE=DialogStyle.SURFACE;
    public static final class Control extends DialogControl {private Control(int id,int x,int y,int width,int height,String label,boolean tab,boolean enabled){super(id,x,y,width,height,label,tab,enabled);}}
    public static List<Control> controls(ProgrammableDialogLayout panel,boolean active,int layout){
        if(!TextureLayoutMode.valid(layout))throw new IllegalArgumentException("Surface layout");List<Control> result=new ArrayList<>();int tabWidth=(panel.listWidth+2)/2;
        result.add(new Control(INACTIVE,panel.listX,panel.top+30,tabWidth-2,18,"Inactive",true,active));
        result.add(new Control(ACTIVE,panel.listX+tabWidth,panel.top+30,tabWidth-2,18,"Active",true,!active));
        result.add(new Control(LAYOUT,panel.controlsX,panel.top+30,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Texture: "+TextureLayoutMode.label(layout),false,true));
        result.add(new Control(CUSTOM,panel.controlsX,panel.top+54,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Custom...",false,true));
        result.add(new Control(DONE,panel.controlsX,panel.doneY,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Done",false,true));return Collections.unmodifiableList(result);
    }
    public static int cycleLayout(int layout,int direction){return DialogInput.cycle(layout,3,direction);}
    private SurfaceDialog(){}
}
