package com.vandorlabs.ui;

import java.util.ArrayList;
import java.util.List;

/** Exact four independent material lists and immediate trigger option transitions. */
public final class TriggerDialog {
    private TriggerDialog(){}
    public static final String TITLE="Programmable Trigger Block";
    public static final class State {
        public final int tab,exact;public final boolean bands;
        public State(int tab,boolean bands,int exact){this.tab=tab;this.bands=bands;this.exact=exact;}
        public State action(int id,int direction){boolean next=id==101?!bands:bands;int tab=id>=90&&id<=93?id-90:this.tab;if(!next&&(tab==1||tab==2))tab=3;return new State(tab,next,id==102?DialogInput.cycle(exact,-1,15,direction):exact);}
    }
    public static final class Control {
        public final ProgrammableDialogLayout.Button bounds;public final boolean visible,enabled;
        private Control(ProgrammableDialogLayout.Button bounds,boolean visible,boolean enabled){this.bounds=bounds;this.visible=visible;this.enabled=enabled;}
    }
    public static String bandsLabel(boolean bands){return bands?"Artwork: Signal bands":"Artwork: Off / On";}
    public static String exactLabel(int exact){return exact<0?"Trigger: Any signal":"Trigger: Exactly "+exact;}
    public static List<Control> controls(ProgrammableDialogLayout panel,State state){List<Control> result=new ArrayList<>();String[] names={"Off","Low 1-5","Mid 6-10","High 11-15"};for(int i=0;i<4;i++)result.add(new Control(panel.tabBounds(90+i,state.bands?i:i==0?0:1,state.bands?4:2,state.bands?names[i]:i==0?"Redstone Off":"Redstone On"),state.bands||i==0||i==3,state.tab!=i));result.add(new Control(panel.controlBounds(101,76,bandsLabel(state.bands)),true,true));result.add(new Control(panel.controlBounds(102,100,exactLabel(state.exact)),true,true));result.add(new Control(panel.doneBounds(100),true,true));return result;}
    public static ProgrammableDialogLayout.Button channels(ProgrammableDialogLayout panel){return new ProgrammableDialogLayout.Button(0,panel.controlsX,panel.top+42,154,18,"");}
}
