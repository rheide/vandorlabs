package com.vandorlabs.ui;

import com.vandorlabs.persistence.ProgrammableGlassSettings;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immediate glass editor; choosing stock artwork retains dormant custom-material options. */
public final class GlassDialog {
    public static final String TITLE="Programmable Glass";
    public static final int GLASS_BASE=0x10000000;
    public static final class State {
        public final int size,shade,tint,material,layout;
        public final boolean join,framed;
        public State(int size,int shade,boolean join,int tint,boolean framed,int material,int layout){this.size=size;this.shade=shade;this.join=join;this.tint=tint;this.framed=framed;this.material=material;this.layout=layout;}
        /** The established editor submits detail size zero; opening alone does not edit the tile. */
        public static State edit(ProgrammableGlassSettings saved){return new State(0,saved.shade,saved.join,saved.tint,saved.framed,saved.material,saved.textureLayout());}
        public int selected(){return material>=0?material:GLASS_BASE+shade;}
        public State choose(int choice){boolean stock=choice>=GLASS_BASE&&choice<GLASS_BASE+GlassTextures.IDS.length;return new State(size,stock?choice-GLASS_BASE:shade,join,tint,framed,stock?-1:choice,layout);}
        public State action(int id,int direction){return new State(size,shade,id==3?!join:join,id==4?DialogInput.cycle(tint,3,direction):tint,id==5?!framed:framed,material,id==6?DialogInput.cycle(layout,3,direction):layout);}
    }
    private GlassDialog(){}
    public static int listY(ProgrammableDialogLayout panel){return panel.top+30;}
    public static int rows(ProgrammableDialogLayout panel){return Math.max(2,(panel.height-64)/22);}
    public static List<ProgrammableDialogLayout.Button> controls(ProgrammableDialogLayout panel,State state){
        List<ProgrammableDialogLayout.Button> out=new ArrayList<>();
        out.add(panel.controlBounds(3,54,"Join: "+(state.join?"On":"Off")));
        out.add(panel.controlBounds(4,78,"Tint: "+GlassTextures.tintLabel(state.tint)));
        out.add(panel.controlBounds(5,102,state.framed?"Framed":"Bare"));
        if(state.material>=0)out.add(panel.controlBounds(6,126,"Texture: "+TextureLayoutMode.label(state.layout)));
        out.add(panel.doneBounds(2));return Collections.unmodifiableList(out);
    }
}
