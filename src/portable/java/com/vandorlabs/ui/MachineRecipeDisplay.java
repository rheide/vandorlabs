package com.vandorlabs.ui;

import com.vandorlabs.machines.SynthesisRecipePolicy;

/** Recipe-browser presentation shared by both platform adapters. */
public final class MachineRecipeDisplay {
    public enum Kind { INFUSING, CHARGING, SYNTHESIS }
    public static final String INFUSER="vandorlabs:infusing",TOWER="vandorlabs:synthesis";
    public static final int WIDTH=174,HEIGHT=104,INPUT_X=14,CATALYST_X=48,OUTPUT_X=140,SLOT_Y=20;
    public static String title(boolean tower){return tower?"Synthesis Tower":"Matter Infuser";}
    public static String[] lines(Kind kind){
        if(kind==Kind.SYNTHESIS)return new String[]{SynthesisRecipePolicy.CORE_RF+" RF / Core",(SynthesisRecipePolicy.CORE_TICKS/20)+" seconds minimum","Select the design in Production.","Supply RF or Battery Modules."};
        return new String[]{kind==Kind.CHARGING?"50 RF/tick; 10,000 RF capacity":"1 iron -> 1 Matter Ingot","Sunlight; 200 working ticks","Dust: 33 ticks; block: 300 ticks","Unused catalyst time is retained."};
    }
    private MachineRecipeDisplay(){}
}
