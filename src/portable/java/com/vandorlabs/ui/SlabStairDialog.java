package com.vandorlabs.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Current slab/stair appearance rows. Changes are immediate; Done only closes the host. */
public final class SlabStairDialog {
    public static final int SIDES=127,RESET_SIDES=128,TREAD=130,ORIENTATION=131;
    private static final String[] FACES={"Down","Up","Front","Back","Left","Right"};
    private SlabStairDialog() { }
    public static final class State {
        public final ProgrammableBlockDialog.State housing;
        public final int sideTexture,tread,roll;
        public State(ProgrammableBlockDialog.State housing,int sideTexture,int tread,int roll){this.housing=housing;this.sideTexture=sideTexture;this.tread=tread;this.roll=roll;}
        public int selectedTexture(){return housing.target==-2?(sideTexture>=0?sideTexture:housing.mainTexture):housing.selectedTexture();}
        private State target(int target,int side){return new State(new ProgrammableBlockDialog.State(housing.mainTexture,housing.layout,housing.facesEnabled,housing.faces(),target),side,tread,roll);}
        public State action(int operation,int direction){
            if(operation==SIDES)return target(-2,sideTexture);
            if(operation==RESET_SIDES)return target(-2,-1);
            if(operation==ORIENTATION)return new State(housing,sideTexture,tread,toggleRoll(roll));
            return new State(housing.action(operation,direction),sideTexture,tread,roll);
        }
        public State choose(int choice){return housing.target==-2?new State(housing,choice,tread,roll):new State(housing.choose(choice),sideTexture,tread,roll);}
        public State chooseTread(int index){return new State(housing,sideTexture,treadForIndex(index),roll);}
    }
    public static int listY(ProgrammableDialogLayout panel){return panel.top+78;}
    public static int listRows(ProgrammableDialogLayout panel){return Math.max(2,(panel.height-108)/ProgrammableDialogLayout.ROW_HEIGHT);}
    public static List<DialogControl> controls(ProgrammableDialogLayout p,State s,boolean stair,String doneLabel){
        List<DialogControl> result=new ArrayList<DialogControl>();
        result.add(new Control(ProgrammableBlockDialog.APPEARANCE,p.left+12,p.top+30,p.width-24,18,"Appearance",true,false));
        result.addAll(tabs(p.listX,p.top,s.housing.facesEnabled?p.width-24:p.listWidth,s.housing.facesEnabled,s.housing.target));
        int y=listY(p);
        for(DialogControl c:ProgrammableBlockDialog.appearanceControls(p.controlsX,y,154,s.housing)){
            if(c.id==ProgrammableBlockDialog.TEXTURE_LAYOUT&&s.housing.target==-2){result.add(resetSides(p.controlsX,y,154,s.sideTexture));y+=24;}
            result.add(new Control(c.id,c.x,y,c.width,c.height,c.label,c.tab,c.enabled));y+=24;
        }
        if(stair){result.add(new Control(TREAD,p.controlsX,y,154,20,treadLabel(s.tread),false,true));y+=24;result.add(orientation(p.controlsX,y,154,s.roll));}
        result.add(new Control(ProgrammableBlockDialog.DONE,p.controlsX,p.doneY,154,20,doneLabel,false,true));
        java.util.SortedSet<Integer> positions=new java.util.TreeSet<Integer>();for(DialogControl c:result)if(!c.tab&&c.id!=ProgrammableBlockDialog.DONE)positions.add(c.y);
        if(!positions.isEmpty()&&positions.last()+20>p.top+p.height-30){int first=positions.first(),step=Math.min(24,(p.top+p.height-30-first)/positions.size());List<Integer> ordered=new ArrayList<Integer>(positions);for(int i=0;i<result.size();i++){DialogControl c=result.get(i);if(!c.tab&&c.id!=ProgrammableBlockDialog.DONE)result.set(i,new Control(c.id,c.x,first+ordered.indexOf(c.y)*step,c.width,Math.min(20,step-2),c.label,c.tab,c.enabled));}}
        return Collections.unmodifiableList(result);
    }
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
