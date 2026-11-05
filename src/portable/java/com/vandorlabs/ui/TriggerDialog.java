package com.vandorlabs.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Current Trigger editor policy; persistence and channel parsing remain host-owned. */
public final class TriggerDialog {
    public static final int FIRST_ARTWORK=90,DONE=100,BANDS=101,EXACT=102,FRONT_ONLY=103,REDSTONE=111,SIDES=112;
    public static final int ARTWORK_PAGE=0,REDSTONE_PAGE=1,SIDES_PAGE=2;
    public static final String TITLE="Programmable Trigger Block",CHANNEL_LABEL="Receive channels";
    private static final String[] BAND_NAMES={"0","1-5","6-10","11-15"};
    private TriggerDialog() { }

    /** Editor cursor and retained drafts, separate from the saved TriggerState schema. */
    public static final class State {
        private final int[] textures,modes,speeds;
        public final int textureTab,page,exact,sidesTexture;
        public final boolean states,frontOnly;
        public State(int[] textures,int[] modes,int[] speeds,int textureTab,int page,int exact,boolean states,boolean frontOnly,int sidesTexture){
            if(textures.length!=4||modes.length!=4||speeds.length!=4)throw new IllegalArgumentException("Four artwork slots required");
            this.textures=textures.clone();this.modes=modes.clone();this.speeds=speeds.clone();
            this.textureTab=textureTab;this.page=page;this.exact=exact;this.states=states;this.frontOnly=frontOnly;this.sidesTexture=sidesTexture;
        }
        public int[] textures(){return textures.clone();}
        public int[] modes(){return modes.clone();}
        public int[] speeds(){return speeds.clone();}
        public int texture(int slot){return textures[slot];}
        public int mode(int slot){return modes[slot];}
        public int speed(int slot){return speeds[slot];}
        public State withArtwork(int slot,int value){int[] next=textures();next[slot]=value;return new State(next,modes,speeds,textureTab,page,exact,states,frontOnly,sidesTexture);}
        public State withSides(int value){return new State(textures,modes,speeds,textureTab,page,exact,states,frontOnly,value);}
        public State choose(int value){return page==SIDES_PAGE?withSides(value):withArtwork(textureTab,value);}
        public State action(int operation,int direction){
            int tab=textureTab,nextPage=page,nextExact=exact;boolean bands=states,front=frontOnly;
            int[] nextModes=modes,nextSpeeds=speeds;
            if(operation==SIDES)nextPage=SIDES_PAGE;
            else if(operation==FRONT_ONLY)front=!front;
            else if(operation==REDSTONE)nextPage=REDSTONE_PAGE;
            else if(operation>=FIRST_ARTWORK&&operation<FIRST_ARTWORK+4){tab=operation-FIRST_ARTWORK;nextPage=ARTWORK_PAGE;}
            else if(operation>=1&&operation<=3){nextModes=modes();nextModes[tab]=operation-1;}
            else if(operation>=10&&operation<=12){nextSpeeds=speeds();nextSpeeds[tab]=operation-10;}
            else if(operation==BANDS){bands=!bands;if(!bands&&(tab==1||tab==2))tab=3;}
            else if(operation==EXACT)nextExact=DialogInput.cycle(exact+1,17,direction)-1;
            else return this;
            return new State(textures,nextModes,nextSpeeds,tab,nextPage,nextExact,bands,front,sidesTexture);
        }
    }
    public static int[] rightClickIds(){return new int[]{BANDS,EXACT,FRONT_ONLY};}
    /** Growing the button list while GuiScreen iterates it must consume this click once. */
    public static boolean safeLeftDispatch(int id){return id==BANDS||id==FRONT_ONLY;}
    public static boolean rebuilds(int id){return id==SIDES||id==REDSTONE||id==FRONT_ONLY||id==BANDS||id>=FIRST_ARTWORK&&id<FIRST_ARTWORK+4;}
    public static boolean sendsImmediately(int id){return id==FRONT_ONLY||id==BANDS||id==EXACT||id>=1&&id<=3||id>=10&&id<=12;}
    public static boolean handles(int id){return rebuilds(id)||sendsImmediately(id);}
    public static boolean enabled(int id,State state){
        if(id>=FIRST_ARTWORK&&id<FIRST_ARTWORK+4)return state.page!=ARTWORK_PAGE||id!=FIRST_ARTWORK+state.textureTab;
        if(id==SIDES)return state.page!=SIDES_PAGE;
        if(id==REDSTONE)return state.page!=REDSTONE_PAGE;
        if(id>=1&&id<=3)return id-1!=state.mode(state.textureTab);
        if(id>=10&&id<=12)return id-10!=state.speed(state.textureTab);
        return true;
    }
    public static String label(int id,State state){
        if(id==BANDS)return state.states?"Artwork: Signal bands":"Artwork: Off / On";
        if(id==EXACT)return state.exact<0?"Trigger: Any signal":"Trigger: Exactly "+state.exact;
        if(id==FRONT_ONLY)return state.frontOnly?"Faces: Front Only":"Faces: All Faces";
        throw new IllegalArgumentException("Unknown Trigger option: "+id);
    }
    public static final class Control extends DialogControl {
        private Control(int id,int x,int y,int width,int height,String label,boolean tab,boolean enabled){super(id,x,y,width,height,label,tab,enabled);}
    }
    private static Control tab(ProgrammableDialogLayout p,State state,int id,int index,int count,String label){int width=(p.width-22)/count;return new Control(id,p.left+12+index*width,p.top+30,width-2,18,label,true,enabled(id,state));}
    public static List<Control> controls(ProgrammableDialogLayout p,State state,String doneLabel){
        List<Control> result=new ArrayList<Control>();int tabs=(state.states?5:3)+(state.frontOnly?1:0);
        for(int i=0;i<4;i++)if(state.states||i==0||i==3)result.add(tab(p,state,FIRST_ARTWORK+i,state.states?i:i==0?0:1,tabs,state.states?BAND_NAMES[i]:i==0?"Off":"On"));
        if(state.frontOnly)result.add(tab(p,state,SIDES,tabs-2,tabs,"Sides"));
        result.add(tab(p,state,REDSTONE,tabs-1,tabs,"Redstone"));
        if(state.page==REDSTONE_PAGE)for(int id=BANDS;id<=FRONT_ONLY;id++)result.add(new Control(id,p.left+12,p.top+96+(id-BANDS)*24,p.width-24,20,label(id,state),false,true));
        result.add(new Control(DONE,p.controlsX,p.doneY,154,20,doneLabel,false,true));return Collections.unmodifiableList(result);
    }
    public static int listY(ProgrammableDialogLayout p){return p.top+54;}
    public static int listRows(ProgrammableDialogLayout p){return p.rows(true);}
    public static final class ChannelBox {
        public final int x,y,width,height;
        private ChannelBox(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;}
    }
    public static ChannelBox channelBox(ProgrammableDialogLayout p){return new ChannelBox(p.left+12,p.top+68,p.width-24,18);}
    public static int channelLabelY(ProgrammableDialogLayout p){return p.top+54;}
}
