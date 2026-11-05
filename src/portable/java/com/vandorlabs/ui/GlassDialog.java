package com.vandorlabs.ui;

import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import java.util.*;

/** Current glass editor rows and immediate scalar choices; frame and playback panes remain host-owned. */
public final class GlassDialog {
    public static final int DONE=2,JOIN=3,TINT=4,FRAMED=5,TEXTURE_LAYOUT=6,FIRST_PAGE=196,PAGE_COUNT=4;
    public static final String TITLE="Programmable Glass";
    private static final String[] PAGES={"Glass","Front","Interior","Exterior"};
    private GlassDialog(){}
    public static final class State {
        public final int size,shade,tint,material,layout,page;
        public final boolean join,framed,stretch;
        public State(int shade,int tint,int material,int layout,boolean join,boolean framed,boolean stretch){this(shade,tint,material,layout,join,framed,stretch,0);}
        private State(int shade,int tint,int material,int layout,boolean join,boolean framed,boolean stretch,int page){this.size=0;this.shade=shade;this.tint=tint;this.material=material;this.layout=layout;this.join=join;this.framed=framed;this.stretch=stretch;this.page=page;}
        public boolean framePage(){return page>0&&page<4;}
        public State action(int operation,int direction){
            if(isPage(operation))return new State(shade,tint,material,layout,join,framed,stretch,operation-FIRST_PAGE);
            if(operation==JOIN)return new State(shade,tint,material,layout,!join,framed,stretch,page);
            if(operation==TINT)return new State(shade,DialogInput.cycle(tint,GlassTextures.TINT_COUNT,direction),material,layout,join,framed,stretch,page);
            if(operation==FRAMED)return withFramed(!framed);
            if(operation==TEXTURE_LAYOUT){int next=DialogInput.cycle(layout,3,direction);return new State(shade,tint,material,next,join,framed,next!=1,page);}
            return this;
        }
        /** Frame-pane callbacks deliberately supply their current live tile value. */
        public State withFramed(boolean value){return new State(shade,tint,material,layout,join,value,stretch,page);}
        public State choose(int choice,int shadeBase){return choice>=shadeBase&&choice<shadeBase+GlassTextures.IDS.length?new State(choice-shadeBase,tint,-1,layout,join,framed,stretch,page):new State(shade,tint,choice,layout,join,framed,stretch,page);}
    }
    public static boolean isPage(int id){return id>=FIRST_PAGE&&id<FIRST_PAGE+PAGE_COUNT;}
    public static int[] rightClickIds(){return new int[]{0,JOIN,TINT,FRAMED,TEXTURE_LAYOUT};}
    public static final class Control extends DialogControl {
        public final boolean visible;
        private Control(int id,int x,int y,int width,int height,String label,boolean tab,boolean enabled,boolean visible){super(id,x,y,width,height,label,tab,enabled);this.visible=visible;}
    }
    public static List<Control> tabs(ProgrammableDialogLayout p,State state){List<Control> rows=new ArrayList<Control>();int width=(p.width-22)/4;for(int i=0;i<4;i++)rows.add(new Control(FIRST_PAGE+i,p.left+12+i*width,p.top+30,width-2,18,PAGES[i],true,state.page!=i,true));return Collections.unmodifiableList(rows);}
    public static List<Control> body(ProgrammableDialogLayout p,State state){
        if(state.framePage())return Collections.emptyList();List<Control> rows=new ArrayList<Control>();
        rows.add(new Control(JOIN,p.controlsX,p.top+54,154,20,"Join: "+(state.join?"On":"Off"),false,true,true));
        rows.add(new Control(TINT,p.controlsX,p.top+78,154,20,"Tint: "+GlassTextures.tintLabel(state.tint),false,true,true));
        rows.add(new Control(FRAMED,p.controlsX,p.top+102,154,20,state.framed?"Framed":"Bare",false,true,false));
        if(state.material>=0)rows.add(new Control(TEXTURE_LAYOUT,p.controlsX,p.top+102,154,20,"Texture: "+TextureLayoutMode.label(state.layout),false,true,true));
        return Collections.unmodifiableList(rows);
    }
    public static Control done(ProgrammableDialogLayout p,String label){return new Control(DONE,p.controlsX,p.doneY,154,20,label,false,true,true);}
    public static int listY(ProgrammableDialogLayout p){return p.top+54;}
    public static int listWidth(ProgrammableDialogLayout p){return p.controlsX-p.left-31;}
    public static int listRows(ProgrammableDialogLayout p){return Math.max(2,(p.height-64)/ProgrammableDialogLayout.ROW_HEIGHT);}
}
