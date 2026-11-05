package com.vandorlabs.ui;

import com.vandorlabs.render.TextureLayoutMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/** Current plain programmable cube appearance controls and immediate edit transitions. */
public final class ProgrammableBlockDialog {
    public static final int DONE=100,TEXTURE_LAYOUT=103,FACE_OVERRIDES=106,INHERIT=108,
        MAIN=120,FIRST_FACE=121,APPEARANCE=195;
    private static final String[] FACE_NAMES={"Down","Up","Front","Back","Left","Right"};
    private ProgrammableBlockDialog() { }
    public static final class State {
        public final int mainTexture,layout,target;
        public final boolean facesEnabled;
        private final int[] faces;
        public State(int mainTexture,int layout,boolean facesEnabled,int[] faces,int target){
            this.mainTexture=mainTexture;this.layout=layout;this.facesEnabled=facesEnabled;
            this.faces=faces.clone();if(faces.length!=6)throw new IllegalArgumentException("Six local faces required");
            this.target=target;
        }
        public int[] faces(){return faces.clone();}
        public int face(int index){return faces[index];}
        public int selectedTexture(){return target<0||!facesEnabled||faces[target]<0?mainTexture:faces[target];}
        public State action(int operation,int direction){
            if(operation==FACE_OVERRIDES)return new State(mainTexture,layout,!facesEnabled,faces,-1);
            if(operation==TEXTURE_LAYOUT)return new State(mainTexture,DialogInput.cycle(layout,3,direction),facesEnabled,faces,target);
            if(operation==MAIN||facesEnabled&&operation>=FIRST_FACE&&operation<FIRST_FACE+6)return new State(mainTexture,layout,facesEnabled,faces,operation-FIRST_FACE);
            if(operation==INHERIT&&target>=0){int[] next=faces();next[target]=-1;return new State(mainTexture,layout,true,next,target);}
            return this;
        }
        public State choose(int choice){
            if(facesEnabled&&target>=0){int[] next=faces();next[target]=choice;return new State(mainTexture,layout,true,next,target);}
            return new State(choice,layout,facesEnabled,faces,target);
        }
    }
    public static final class Control {
        public final int id,x,y,width,height;
        public final String label;
        public final boolean tab,enabled;
        private Control(int id,int x,int y,int width,int height,String label,boolean tab,boolean enabled){
            this.id=id;this.x=x;this.y=y;this.width=width;this.height=height;this.label=label;this.tab=tab;this.enabled=enabled;
        }
    }
    public static int listY(ProgrammableDialogLayout panel,State state){return panel.top+(state.facesEnabled?78:54);}
    /** Includes the search row; the picker displays one fewer artwork row. */
    public static int listRows(ProgrammableDialogLayout panel,State state){return Math.max(2,(panel.height-(state.facesEnabled?108:84))/ProgrammableDialogLayout.ROW_HEIGHT);}
    public static List<Control> controls(ProgrammableDialogLayout p,State s,String doneLabel){
        List<Control> result=new ArrayList<Control>();
        result.add(new Control(APPEARANCE,p.left+12,p.top+30,p.width-24,18,"Appearance",true,false));
        if(s.facesEnabled){int span=p.width-24,w=span/7;for(int i=0;i<7;i++)result.add(new Control(i==0?MAIN:FIRST_FACE+i-1,p.listX+i*w,p.top+54,i==6?span-i*w-2:w-2,18,i==0?"Main":FACE_NAMES[i-1],true,s.target!=i-1));}
        int y=listY(p,s);
        result.add(new Control(FACE_OVERRIDES,p.controlsX,y,154,20,"Face overrides: "+(s.facesEnabled?"On":"Off"),false,true));y+=24;
        if(s.facesEnabled){result.add(new Control(INHERIT,p.controlsX,y,154,20,s.target<0?"Select a face":s.face(s.target)<0?"Using main texture":"Use main texture",false,s.target>=0&&s.face(s.target)>=0));y+=24;}
        result.add(new Control(TEXTURE_LAYOUT,p.controlsX,y,154,20,"Texture: "+TextureLayoutMode.label(s.layout),false,true));
        result.add(new Control(DONE,p.controlsX,p.doneY,154,20,doneLabel,false,true));
        SortedSet<Integer> positions=new TreeSet<Integer>();for(Control c:result)if(!c.tab&&c.id!=DONE)positions.add(c.y);
        if(!positions.isEmpty()&&positions.last()+20>p.top+p.height-30){
            int first=positions.first(),step=Math.min(24,(p.top+p.height-30-first)/positions.size());List<Integer> ordered=new ArrayList<Integer>(positions);
            for(int i=0;i<result.size();i++){Control c=result.get(i);if(!c.tab&&c.id!=DONE)result.set(i,new Control(c.id,c.x,first+ordered.indexOf(c.y)*step,c.width,Math.min(20,step-2),c.label,false,c.enabled));}
        }
        return Collections.unmodifiableList(result);
    }
}
