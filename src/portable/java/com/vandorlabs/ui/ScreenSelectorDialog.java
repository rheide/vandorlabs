package com.vandorlabs.ui;

import com.vandorlabs.render.TextureLayoutMode;
import java.util.*;
import java.util.function.UnaryOperator;

/** Shared screen/console option order, labels, operation IDs and transitions. */
public final class ScreenSelectorDialog {
    private ScreenSelectorDialog(){}
    public static final class State {
        public final boolean console,framed,hasFramePair;
        public final int tab,mode,speed,trigger,sides;
        public State(boolean console,int tab,int mode,int speed,int trigger,int sides,boolean framed,boolean hasFramePair){this.console=console;this.tab=tab;this.mode=mode;this.speed=speed;this.trigger=trigger;this.sides=sides;this.framed=framed;this.hasFramePair=hasFramePair;}
        public State action(int id,int direction){
            return new State(console,id>=90&&id<=(console?92:91)?id-90:tab,id>=1&&id<=3?id-1:mode,id>=10&&id<=12?id-10:speed,id==0?DialogInput.cycle(trigger,3,direction):trigger,id==32?DialogInput.cycle(sides,3,direction):sides,id==4&&hasFramePair?!framed:framed,hasFramePair);
        }
        public boolean redstoneEnabled(){return trigger!=0;}
        public boolean redstoneInverted(){return trigger==2;}
    }
    public static final class Control {
        public final ProgrammableDialogLayout.Button bounds;
        public final boolean enabled;
        private Control(ProgrammableDialogLayout.Button bounds,boolean enabled){this.bounds=bounds;this.enabled=enabled;}
    }
    public static List<Control> controls(ProgrammableDialogLayout panel,State state,UnaryOperator<String> translate){
        List<Control> result=new ArrayList<>();int tabs=state.console?3:2;
        result.add(new Control(panel.tabBounds(90,0,tabs,"Screen"),state.tab!=0));
        if(state.console)result.add(new Control(panel.tabBounds(91,1,tabs,"Controls"),state.tab!=1));
        result.add(new Control(panel.tabBounds(state.console?92:91,tabs-1,tabs,"Housing"),state.tab!=tabs-1));
        String[] modes={"off","static","animated"},speeds={"slow","normal","fast"};
        for(int i=0;i<3;i++)result.add(new Control(panel.choiceBounds(i+1,i,3,42,translate.apply("gui.vandorlabs.selector."+modes[i])),state.mode!=i));
        for(int i=0;i<3;i++)result.add(new Control(panel.choiceBounds(i+10,i,3,76,translate.apply("gui.vandorlabs.selector."+speeds[i])),state.speed!=i));
        result.add(new Control(panel.controlBounds(4,98,(state.framed?"[x] ":"[ ] ")+translate.apply("gui.vandorlabs.selector.framed")),state.hasFramePair));
        result.add(new Control(panel.controlBounds(0,120,"Trigger: "+(state.trigger==0?"Disabled":state.trigger==2?"Redstone OFF":"Redstone ON")),true));
        result.add(new Control(panel.controlBounds(32,142,"Sides: "+TextureLayoutMode.label(state.sides)),true));
        result.add(new Control(panel.doneBounds(20),true));
        return result;
    }
    public static ProgrammableDialogLayout.Button channelBounds(ProgrammableDialogLayout panel){return new ProgrammableDialogLayout.Button(40,panel.controlsX,panel.top+177,154,18,"");}
}
