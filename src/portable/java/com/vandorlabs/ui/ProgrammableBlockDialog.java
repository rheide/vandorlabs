package com.vandorlabs.ui;

import com.vandorlabs.render.TextureLayoutMode;
import java.util.*;

/** The full programmable block's settings, operation IDs and logical dialog. */
public final class ProgrammableBlockDialog {
    public static final int DONE=100,TEXTURE_LAYOUT=103,FACE_OVERRIDES=106,INHERIT=108,MAIN=120,FIRST_FACE=121;
    public static final String MAIN_TEXTURE_KEY=com.vandorlabs.persistence.SaveSchema.Screen.HOUSING_TEXTURE,LAYOUT_KEY="MainTextureLayout",FACES_ENABLED_KEY="FaceTexturesEnabled",FACES_KEY="FaceTextures";
    private static final String[] FACE_LABELS={"Bottom","Top","Front","Back","Left","Right"};
    private ProgrammableBlockDialog(){}
    /** The pre-versioned catalog contained a vent grille at index 14. */
    public static int savedMainTexture(int saved,boolean versioned){return versioned?saved:saved==14?0:saved>14?saved-1:saved;}
    public static final class State {
        public final int mainTexture,layout,target;
        public final boolean facesEnabled;
        private final int[] faces;
        public State(int mainTexture,int layout,boolean facesEnabled,int[] faces,int target){
            this.mainTexture=mainTexture;this.layout=TextureLayoutMode.valid(layout)?layout:TextureLayoutMode.STRETCH;
            this.facesEnabled=facesEnabled;this.faces=new int[]{-1,-1,-1,-1,-1,-1};
            if(faces!=null&&faces.length==6)for(int i=0;i<6;i++)this.faces[i]=Math.max(-1,faces[i]);
            this.target=facesEnabled&&target>=0&&target<6?target:-1;
        }
        public int[] faces(){return faces.clone();}
        public int face(int index){return faces[index];}
        public int selectedTexture(){return target>=0&&faces[target]>=0?faces[target]:mainTexture;}
        public State action(int operation,int direction){
            if(operation==FACE_OVERRIDES)return new State(mainTexture,layout,!facesEnabled,faces,-1);
            if(operation==TEXTURE_LAYOUT)return new State(mainTexture,DialogInput.cycle(layout,3,direction),facesEnabled,faces,target);
            if(operation==MAIN||facesEnabled&&operation>=FIRST_FACE&&operation<FIRST_FACE+6)return new State(mainTexture,layout,facesEnabled,faces,operation-FIRST_FACE);
            if(operation==INHERIT&&target>=0){int[] next=faces();next[target]=-1;return new State(mainTexture,layout,facesEnabled,next,target);}
            return this;
        }
        public State choose(int choice){
            if(target<0)return new State(choice,layout,facesEnabled,faces,target);
            int[] next=faces();next[target]=choice;return new State(mainTexture,layout,facesEnabled,next,target);
        }
    }
    public static final class Control {
        public final ProgrammableDialogLayout.Button bounds;
        public final boolean enabled;
        private Control(ProgrammableDialogLayout.Button bounds,boolean enabled){this.bounds=bounds;this.enabled=enabled;}
    }
    public static int listY(ProgrammableDialogLayout panel,State state){return panel.top+(state.facesEnabled?54:30);}
    public static int listRows(ProgrammableDialogLayout panel,State state){return Math.max(2,(panel.height-(state.facesEnabled?60:64))/ProgrammableDialogLayout.ROW_HEIGHT);}
    public static List<Control> controls(ProgrammableDialogLayout panel,State state){return controls(panel,state,"Done");}
    public static List<Control> controls(ProgrammableDialogLayout panel,State state,String doneLabel){
        List<Control> result=new ArrayList<>();
        if(state.facesEnabled){
            int span=panel.width-24,tabWidth=span/7;
            for(int i=0;i<7;i++)result.add(new Control(new ProgrammableDialogLayout.Button(i==0?MAIN:FIRST_FACE+i-1,panel.listX+i*tabWidth,panel.top+30,i==6?span-i*tabWidth-2:tabWidth-2,18,i==0?"Main":FACE_LABELS[i-1]),state.target!=i-1));
        }
        int y=state.facesEnabled?54:30;
        result.add(new Control(panel.controlBounds(FACE_OVERRIDES,y,"Face overrides: "+(state.facesEnabled?"On":"Off")),true));y+=24;
        if(state.facesEnabled){
            result.add(new Control(panel.controlBounds(INHERIT,y,state.target<0?"Select a face":state.face(state.target)<0?"Using main texture":"Use main texture"),state.target>=0&&state.face(state.target)>=0));y+=24;
        }
        result.add(new Control(panel.controlBounds(TEXTURE_LAYOUT,y,"Texture: "+TextureLayoutMode.label(state.layout)),true));
        ProgrammableDialogLayout.Button done=panel.doneBounds(DONE);
        result.add(new Control(new ProgrammableDialogLayout.Button(done.id,done.x,done.y,done.width,done.height,doneLabel),true));
        return result;
    }
}
