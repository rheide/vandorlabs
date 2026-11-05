package com.vandorlabs.ui;

import com.vandorlabs.persistence.FrameSettings;
import com.vandorlabs.persistence.WallSettings;
import com.vandorlabs.render.TextureLayoutMode;
import java.util.*;

/** Current plain wall Appearance/Exterior panes; every material choice is immediate. */
public final class PlainWallDialog {
    public static final int APPEARANCE=195,EXTERIOR=197;
    private PlainWallDialog(){}
    public static final class State {
        public final SlabStairDialog.State base;
        public final WallSettings walls;
        /** Last published frame settings; opening a pane must not leak its normalization into other edits. */
        public final FrameSettings frames;
        public final FrameSettings frameDraft;
        public final boolean exterior,editorInitialized;
        private final int defaultScreen;
        public State(SlabStairDialog.State base,WallSettings walls,FrameSettings frames,int defaultScreen){this(base,walls,frames,null,defaultScreen,false,false);}
        private State(SlabStairDialog.State base,WallSettings walls,FrameSettings frames,FrameSettings draft,int defaultScreen,boolean exterior,boolean initialized){this.base=base;this.walls=walls;this.frames=frames;frameDraft=draft;this.defaultScreen=defaultScreen;this.exterior=exterior;editorInitialized=initialized;}
        public int selectedTexture(){return exterior?frameDraft.interior:base.housing.mainTexture;}
        public State action(int operation,int direction){
            if(operation==EXTERIOR)return new State(base,walls,frames,editorInitialized?frameDraft:frames.editor(false,false,defaultScreen),defaultScreen,true,true);
            if(operation==APPEARANCE)return new State(base,walls,frames,frameDraft,defaultScreen,false,editorInitialized);
            if(operation==ProgrammableBlockDialog.TEXTURE_LAYOUT)return new State(base.action(operation,direction),walls,frames,frameDraft,defaultScreen,exterior,editorInitialized);
            return this;
        }
        public State choose(int choice){
            if(!exterior)return new State(base.choose(choice),walls,frames,frameDraft,defaultScreen,false,editorInitialized);
            FrameSettings next=new FrameSettings(frameDraft.front,choice,frameDraft.exterior,frameDraft.artworkVersion,frameDraft.splitTop,frameDraft.splitBottom,frameDraft.tile,frameDraft.displayFit,frameDraft.split,frameDraft.bare);
            return new State(base,walls,next,next,defaultScreen,true,true);
        }
    }
    private static final class Control extends DialogControl {
        private Control(int id,int x,int y,int width,int height,String label,boolean tab,boolean enabled){super(id,x,y,width,height,label,tab,enabled);}
    }
    public static int listY(ProgrammableDialogLayout panel){return panel.top+54;}
    public static int listWidth(ProgrammableDialogLayout panel,State state){return state.exterior?panel.width-24:panel.listWidth;}
    public static int listRows(ProgrammableDialogLayout panel,State state){return Math.max(2,(panel.height-(state.exterior?110:84))/ProgrammableDialogLayout.ROW_HEIGHT);}
    public static List<DialogControl> controls(ProgrammableDialogLayout p,State s,String done){
        List<DialogControl> result=new ArrayList<DialogControl>();int width=(p.width-22)/2;
        result.add(new Control(APPEARANCE,p.left+12,p.top+30,width-2,18,"Appearance",true,s.exterior));result.add(new Control(EXTERIOR,p.left+12+width,p.top+30,width-2,18,"Exterior",true,!s.exterior));
        if(!s.exterior)result.add(new Control(ProgrammableBlockDialog.TEXTURE_LAYOUT,p.controlsX,p.top+54,154,20,"Texture: "+TextureLayoutMode.label(s.base.housing.layout),false,true));
        result.add(new Control(ProgrammableBlockDialog.DONE,p.controlsX,p.doneY,154,20,done,false,true));return Collections.unmodifiableList(result);
    }
}
