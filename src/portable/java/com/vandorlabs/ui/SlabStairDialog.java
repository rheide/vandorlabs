package com.vandorlabs.ui;

import com.vandorlabs.persistence.StairState;
import com.vandorlabs.render.TextureLayoutMode;
import java.util.ArrayList;
import java.util.List;

/** Immediate material/side/face settings and stair controls in the legacy housing picker. */
public final class SlabStairDialog {
    public static final int SIDE=127,RESET_SIDE=128,TREAD=130,ROLL=131;
    private static final String[] FACES={"Bottom","Top","Front","Back","Left","Right"};
    private SlabStairDialog(){}
    public static final class State {
        public final int main,side,layout,target;public final boolean facesEnabled;public final StairState stairs;
        private final int[] faces;
        public State(int main,int side,int layout,boolean enabled,int[] faces,int target,StairState stairs){this.main=main;this.side=side;this.layout=layout;facesEnabled=enabled;this.faces=faces.clone();this.target=target;this.stairs=stairs;}
        public int[] faces(){return faces.clone();}
        public int selected(){return target==-2?(side>=0?side:main):target>=0&&facesEnabled&&faces[target]>=0?faces[target]:main;}
        public State action(int id,int direction){
            int target=this.target,layout=this.layout,side=this.side;boolean enabled=facesEnabled;int[] faces=faces();StairState stairs=this.stairs;
            if(id==106){enabled=!enabled;target=-1;}
            if(id==103)layout=DialogInput.cycle(layout,3,direction);
            if(id==120||enabled&&id>=121&&id<=126)target=id-121;
            if(id==SIDE||id==RESET_SIDE){target=-2;if(id==RESET_SIDE)side=-1;}
            if(id==108&&target>=0)faces[target]=-1;
            if(id==ROLL)stairs=stairs.toggleRoll();
            return new State(main,side,layout,enabled,faces,target,stairs);
        }
        public State choose(int choice){int[] faces=faces();if(target==-2)return new State(main,choice,layout,facesEnabled,faces,target,stairs);if(target>=0&&facesEnabled){faces[target]=choice;return new State(main,side,layout,facesEnabled,faces,target,stairs);}return new State(choice,side,layout,facesEnabled,faces,target,stairs);}
        public State slider(int index){return new State(main,side,layout,facesEnabled,faces,target,stairs.slider(index));}
    }
    public static final class Control {
        public final ProgrammableDialogLayout.Button bounds;public final boolean enabled,slider;
        private Control(ProgrammableDialogLayout.Button b,boolean enabled,boolean slider){bounds=b;this.enabled=enabled;this.slider=slider;}
    }
    public static int listY(ProgrammableDialogLayout p){return p.top+54;}
    public static String stepLabel(StairState state){return "Step size: "+state.tread+" px";}
    public static int rows(ProgrammableDialogLayout p){return Math.max(2,(p.height-60)/22);}
    public static List<Control> controls(ProgrammableDialogLayout p,State state,boolean stair,String done){
        List<Control> out=new ArrayList<>();int count=state.facesEnabled?8:2,span=state.facesEnabled?p.width-24:p.listWidth,w=span/count;
        for(int i=0;i<count;i++){int target=i==0?-1:i==1?-2:i-2;int id=i==0?120:i==1?SIDE:121+target;out.add(new Control(new ProgrammableDialogLayout.Button(id,p.listX+i*w,p.top+30,i==count-1?span-i*w-2:w-2,18,i==0?"Main":i==1?"Side":FACES[target]),state.target!=target,false));}
        int y=state.facesEnabled?54:30;out.add(new Control(p.controlBounds(106,y,"Face overrides: "+(state.facesEnabled?"On":"Off")),true,false));y+=24;
        if(state.facesEnabled){out.add(new Control(p.controlBounds(108,y,state.target<0?"Select a face":state.faces[state.target]<0?"Using main texture":"Use main texture"),state.target>=0&&state.faces[state.target]>=0,false));y+=24;}
        if(state.target==-2){out.add(new Control(p.controlBounds(RESET_SIDE,y,"Use default side texture"),state.side>=0,false));y+=24;}
        out.add(new Control(p.controlBounds(103,y,"Texture: "+TextureLayoutMode.label(state.layout)),true,false));y+=24;
        if(stair){out.add(new Control(p.controlBounds(TREAD,y,stepLabel(state.stairs)),true,true));y+=24;out.add(new Control(p.controlBounds(ROLL,y,"Orientation: "+(state.stairs.roll==0?"Normal":"Sideways")),true,false));}
        ProgrammableDialogLayout.Button b=p.doneBounds(100);out.add(new Control(new ProgrammableDialogLayout.Button(b.id,b.x,b.y,b.width,b.height,done),true,false));return out;
    }
}
