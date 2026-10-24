package com.vandorlabs.ui;

import java.util.*;
import java.util.function.ToIntFunction;

/** Logical row-editor controls shared by regular screens, cockpit dialogs and adapters. */
public final class RedstoneDialogLayout {
    public final int left,top,width,height,controlsX,rowListTop;
    public final boolean header;
    public RedstoneDialogLayout(int screenWidth,int screenHeight,boolean header){
        this.header=header;width=Math.min(420,screenWidth-12);height=Math.min(300,screenHeight-12);
        left=(screenWidth-width)/2;top=(screenHeight-height)/2;controlsX=left+width-164;rowListTop=header?82:54;
    }
    public int visibleRows(){return Math.max(1,(height-rowListTop-12)/20);}
    public ProgrammableDialogLayout.Button title(){return new ProgrammableDialogLayout.Button(2,left+78,top+50,width-258,18,"");}
    public ProgrammableDialogLayout.Button label(){return new ProgrammableDialogLayout.Button(0,controlsX,top+105,148,18,"");}
    public ProgrammableDialogLayout.Button channels(){return new ProgrammableDialogLayout.Button(1,controlsX,top+138,148,18,"");}
    public static final class Control {
        public final ProgrammableDialogLayout.Button bounds;public final boolean visible,enabled;
        private Control(ProgrammableDialogLayout.Button bounds,boolean visible,boolean enabled){this.bounds=bounds;this.visible=visible;this.enabled=enabled;}
    }
    private Control button(int id,int x,int y,int width,int height,String text,boolean visible,boolean enabled){return new Control(new ProgrammableDialogLayout.Button(id,x,y,width,height,text),visible,enabled);}
    public List<Control> controls(boolean materials,int selected,int rows,int maxRows,int kind,int low,int high){
        List<Control> controls=new ArrayList<>();boolean editing=!materials&&selected>=0;
        controls.add(button(0,left+12,top+28,76,20,"Rows",true,materials));
        controls.add(button(1,left+92,top+28,90,20,"Housing",true,!materials));
        controls.add(button(2,controlsX,top+54,70,18,"Add",!materials,rows<maxRows));
        controls.add(button(3,controlsX+76,top+54,72,18,"Remove",!materials,selected>=0));
        controls.add(button(5,controlsX,top+74,70,18,"Up",!materials,selected>0));
        controls.add(button(6,controlsX+76,top+74,72,18,"Down",!materials,selected>=0&&selected<rows-1));
        controls.add(button(7,controlsX,top+160,148,18,"Control: "+(kind==2?"Pulse":kind==1?"Slider":"Toggle"),editing,true));
        controls.add(button(8,controlsX,top+182,70,18,"Low: "+low,editing,kind==1));
        controls.add(button(9,controlsX+76,top+182,72,18,"High: "+high,editing,kind==1));
        controls.add(button(4,controlsX,top+height-22,148,20,"Done",true,true));return controls;
    }
    public static int nextKind(int kind,int direction){return DialogInput.cycle(kind,3,direction);}
    public static int nextLow(int low,int high,int direction){return DialogInput.cycle(low,1,high-2,direction);}
    public static int nextHigh(int low,int high,int direction){return DialogInput.cycle(high,low+2,15,direction);}
    public static String sliderLabel(String label,ToIntFunction<String> width){while(label.length()>11||width.applyAsInt(label)>50)label=label.substring(0,label.length()-1);return label;}
    public static int moveRows(List<?> labels,List<?> channels,int selected,int delta){
        int next=selected+delta;
        if(labels.size()!=channels.size()||selected<0||selected>=labels.size()||Math.abs(delta)!=1||next<0||next>=labels.size())return selected;
        Collections.swap(labels,selected,next);Collections.swap(channels,selected,next);return next;
    }
}
