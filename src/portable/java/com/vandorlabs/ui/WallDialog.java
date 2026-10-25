package com.vandorlabs.ui;

import com.vandorlabs.persistence.WallAppearance;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import java.util.ArrayList;
import java.util.List;

/** The flat wall and thin/full porthole editors; diagonal controls deliberately remain separate. */
public final class WallDialog {
    public static final String TITLE_KEY="gui.vandorlabs.wall.title";
    public static final int GLASS_BASE=0x10000000,DEFAULT_SIDE=26;
    private static final String[] SHAPES={"Hexagon","Octagon","Square","Round"};
    public enum Profile {PLAIN,PORTHOLE,FULL_PORTHOLE;public boolean porthole(){return this!=PLAIN;}}
    public enum Effect {NONE,MAIN,SIDE,LAYOUT,APPEARANCE,CLOSE}
    public static final class State {
        public final int main,side,layout,target;public final WallAppearance appearance;
        public State(int main,int side,int layout,int target,WallAppearance appearance){this.main=main;this.side=side;this.layout=layout;this.target=target;this.appearance=appearance;}
        /** Preserve local editor options while sampling independently synchronized tile fields. */
        public State withLive(State live){return snapshot(live.main,live.side,layout,target,appearance.shade,appearance.join,appearance.shape,live.appearance);}
        public int selected(){return selected(appearance.shade);}
        /** Legacy labels use local options while the stock picker samples the latest synchronized shade. */
        public int selected(int displayedShade){return target==-3?(appearance.material<0?GLASS_BASE+displayedShade:appearance.material):target==-2?(side>=0?side:DEFAULT_SIDE):main;}
        public State action(int id,int direction){
            int target=this.target,side=this.side,layout=this.layout;WallAppearance a=appearance;
            if(id==120)target=-1;if(id==127||id==128){target=-2;if(id==128)side=-1;}if(id==129)target=-3;
            if(id==103)layout=DialogInput.cycle(layout,3,direction);
            if(id==102||id==104||id==113||id==114||id==115||id==116){int mode=id==115?DialogInput.cycle(a.textureLayout(),3,direction):a.textureLayout();a=a.withOptions(id==102?!a.join:a.join,id==104?DialogInput.cycle(a.shape,4,direction):a.shape,id==113?DialogInput.cycle(a.tint,3,direction):a.tint,id==114?!a.framed:a.framed,mode,id==116?!a.textureJoin:a.textureJoin);}
            return new State(main,side,layout,target,a);
        }
        public State choose(int choice){if(target==-3)return new State(main,side,layout,target,choice>=GLASS_BASE&&choice<GLASS_BASE+GlassTextures.IDS.length?appearance.withGlass(choice-GLASS_BASE,-1):appearance.withGlass(appearance.shade,choice));return new State(target==-2?main:choice,target==-2?choice:side,layout,target,appearance);}
        public Effect choiceEffect(){return target==-3?Effect.APPEARANCE:target==-2?Effect.SIDE:Effect.MAIN;}
    }
    public static final class Control {
        public final ProgrammableDialogLayout.Button bounds;public final boolean enabled;
        private Control(ProgrammableDialogLayout.Button bounds,boolean enabled){this.bounds=bounds;this.enabled=enabled;}
    }
    public static State snapshot(int main,int side,int layout,int target,int localShade,boolean localJoin,int localShape,WallAppearance live){return new State(main,side,layout,target,new WallAppearance(localShade,live.tint,live.material,live.framed,live.stretch,live.textureJoin,localJoin,localShape,live.layout));}
    /** The legacy editor sends independent Main, Side, Layout and Appearance commands. */
    public static State applyEffect(State current,State incoming,Effect effect){
        WallAppearance a=current.appearance;
        if(effect==Effect.APPEARANCE){WallAppearance next=incoming.appearance;a=next.withOptions(next.join,next.shape,next.tint,next.framed,next.textureLayout(),next.textureJoin);}
        return new State(effect==Effect.MAIN?incoming.main:current.main,effect==Effect.SIDE?incoming.side:current.side,effect==Effect.LAYOUT?incoming.layout:current.layout,current.target,a);
    }
    public static State fresh(Profile profile){return new State(0,-1,0,-1,WallAppearance.fresh(profile.porthole()));}
    private WallDialog(){}
    public static boolean handles(Profile p,State state,int id){return id==100||id==103||id==120||p.porthole()&&(id==102||id==104||id==127||id==128||id==129||state.target==-3&&(id==113||id==114||id==115||id==116));}
    public static boolean resetView(int id){return id==129;}
    public static Effect effect(int id){return id==100?Effect.CLOSE:id==128?Effect.SIDE:id==103?Effect.LAYOUT:id==102||id==104||id==113||id==114||id==115||id==116?Effect.APPEARANCE:Effect.NONE;}
    public static boolean rebuild(int id){return id!=100&&id!=102&&id!=103&&id!=104;}
    public static int listY(ProgrammableDialogLayout panel,Profile p){return panel.top+(p.porthole()?54:30);}
    public static int rows(ProgrammableDialogLayout panel,Profile p){return Math.max(2,(panel.height-(p.porthole()?60:64))/22);}
    public static String layoutLabel(int layout){return "Texture: "+TextureLayoutMode.label(layout);}
    public static String joinLabel(boolean join){return "Join: "+(join?"On":"Off");}
    public static String shapeLabel(int shape){return "Shape: "+SHAPES[shape];}
    public static List<Control> controls(ProgrammableDialogLayout panel,Profile p,State state,String done){
        List<Control> out=new ArrayList<>();if(p.porthole()){int w=panel.listWidth/3;int[] ids={120,127,129},targets={-1,-2,-3};String[] labels={"Main","Side","Glass"};for(int i=0;i<3;i++)out.add(new Control(new ProgrammableDialogLayout.Button(ids[i],panel.listX+i*w,panel.top+30,i==2?panel.listWidth-i*w-2:w-2,18,labels[i]),state.target!=targets[i]));}
        int y=30;if(state.target==-2){out.add(new Control(panel.controlBounds(128,y,"Use default side texture"),state.side>=0));y+=24;}
        if(state.target!=-3){out.add(new Control(panel.controlBounds(103,y,layoutLabel(state.layout)),true));y+=24;}
        if(p.porthole()){out.add(new Control(panel.controlBounds(102,y,joinLabel(state.appearance.join)),true));y+=24;out.add(new Control(panel.controlBounds(104,y,shapeLabel(state.appearance.shape)),true));y+=24;}
        if(state.target==-3){out.add(new Control(panel.controlBounds(113,y,"Tint: "+GlassTextures.tintLabel(state.appearance.tint)),true));y+=24;if(state.appearance.material>=0)out.add(new Control(panel.controlBounds(115,y,state.appearance.textureLayout()==2?"Texture: Fit (aspect)":state.appearance.textureLayout()==1?"Texture: Tile":"Texture: Stretch"),true));}
        ProgrammableDialogLayout.Button b=panel.doneBounds(100);out.add(new Control(new ProgrammableDialogLayout.Button(b.id,b.x,b.y,b.width,b.height,done),true));return out;
    }
}
