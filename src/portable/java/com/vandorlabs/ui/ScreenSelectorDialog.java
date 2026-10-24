package com.vandorlabs.ui;

import com.vandorlabs.render.TextureLayoutMode;
import java.util.*;
import java.util.function.UnaryOperator;

/** Shared screen/console option order, labels, operation IDs and transitions. */
public final class ScreenSelectorDialog {
    private ScreenSelectorDialog(){}
    public enum Profile {
        SCREEN(new String[]{"Screen","Housing"},false,false,false),
        CONSOLE(new String[]{"Screen","Controls","Housing"},false,false,false),
        INPUT(new String[]{"Controls","Housing"},true,true,true),
        DIAGONAL_HALF(new String[]{"Controls","Housing"},true,true,false),
        HALF_CONSOLE(new String[]{"Bottom","Top","Housing"},true,false,false);
        private final String[] tabs;private final boolean interleave,input,size;
        public final int modeY,speedY,frameY,triggerY,sidesY,channelY,displayCaptionY,speedCaptionY,channelCaptionY;
        Profile(String[] tabs,boolean interleave,boolean input,boolean size){this.tabs=tabs;this.interleave=interleave;this.input=input;this.size=size;modeY=input?38:42;speedY=input?68:76;frameY=input?108:98;triggerY=input?130:120;sidesY=input?152:142;channelY=input?185:177;displayCaptionY=input?26:30;speedCaptionY=input?58:64;channelCaptionY=input?174:166;}
        public int tabs(){return tabs.length;}
    }
    public static final class State {
        public final boolean console,framed,hasFramePair,smallInput;
        public final int tab,mode,speed,trigger,sides;
        public State(boolean console,int tab,int mode,int speed,int trigger,int sides,boolean framed,boolean hasFramePair){this(console,tab,mode,speed,trigger,sides,framed,hasFramePair,false);}
        public State(boolean console,int tab,int mode,int speed,int trigger,int sides,boolean framed,boolean hasFramePair,boolean smallInput){this.console=console;this.tab=tab;this.mode=mode;this.speed=speed;this.trigger=trigger;this.sides=sides;this.framed=framed;this.hasFramePair=hasFramePair;this.smallInput=smallInput;}
        public State action(int id,int direction){
            return new State(console,id>=90&&id<=(console?92:91)?id-90:tab,id>=1&&id<=3?id-1:mode,id>=10&&id<=12?id-10:speed,id==0?DialogInput.cycle(trigger,3,direction):trigger,id==32?DialogInput.cycle(sides,3,direction):sides,id==4&&hasFramePair?!framed:framed,hasFramePair,id==30?true:id==31?false:smallInput);
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
        return controls(panel,state,state.console?Profile.CONSOLE:Profile.SCREEN,translate);
    }
    public static List<Control> controls(ProgrammableDialogLayout panel,State state,Profile profile,UnaryOperator<String> translate){
        List<Control> result=new ArrayList<>();int tabs=profile.tabs.length;
        for(int i=0;i<tabs;i++)result.add(new Control(panel.tabBounds(90+i,i,tabs,profile.tabs[i]),state.tab!=i));
        String[] modes={"off","static","animated"},speeds={"slow","normal","fast"};
        for(int i=0;i<3;i++){
            result.add(new Control(panel.choiceBounds(i+1,i,3,profile.modeY,translate.apply("gui.vandorlabs.selector."+modes[i])),state.mode!=i));
            if(profile.interleave)result.add(new Control(panel.choiceBounds(i+10,i,3,profile.speedY,translate.apply("gui.vandorlabs.selector."+speeds[i])),state.speed!=i));
        }
        if(!profile.interleave)for(int i=0;i<3;i++)result.add(new Control(panel.choiceBounds(i+10,i,3,profile.speedY,translate.apply("gui.vandorlabs.selector."+speeds[i])),state.speed!=i));
        if(profile.size){result.add(new Control(panel.choiceBounds(30,0,2,88,translate.apply("gui.vandorlabs.input.small")),!state.smallInput));result.add(new Control(panel.choiceBounds(31,1,2,88,translate.apply("gui.vandorlabs.selector.normal")),state.smallInput));}
        String frame=profile==Profile.SCREEN||profile==Profile.CONSOLE?translate.apply("gui.vandorlabs.selector.framed"):"Framed";
        result.add(new Control(panel.controlBounds(4,profile.frameY,(state.framed?"[x] ":"[ ] ")+frame),state.hasFramePair));
        result.add(new Control(panel.controlBounds(0,profile.triggerY,"Trigger: "+(state.trigger==0?"Disabled":state.trigger==2?"Redstone OFF":"Redstone ON")),true));
        result.add(new Control(panel.controlBounds(32,profile.sidesY,(profile.input?"Wall texture: ":"Sides: ")+TextureLayoutMode.label(state.sides)),true));
        result.add(new Control(panel.doneBounds(20),true));return result;
    }
    public static ProgrammableDialogLayout.Button channelBounds(ProgrammableDialogLayout panel){return channelBounds(panel,Profile.SCREEN);}
    public static ProgrammableDialogLayout.Button channelBounds(ProgrammableDialogLayout panel,Profile profile){return new ProgrammableDialogLayout.Button(40,panel.controlsX,panel.top+profile.channelY,154,18,"");}
}
