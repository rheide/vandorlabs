package com.vandorlabs.persistence;

/** Stable appearance indices: append designs, never reorder existing entries. */
public final class SpaceDoorData {
    public static final int TRIGGER_DISABLED=0, TRIGGER_REDSTONE_ON=1, TRIGGER_REDSTONE_OFF=2;
    public final int design,detail,direction;
    public final int trigger;
    public final boolean framed,sliding;
    public final boolean middle, hinges, panel;
    public SpaceDoorData(int design,int detail,boolean framed,int direction) {
        this(design,detail,framed,direction,false,false);
    }
    public SpaceDoorData(int design,int detail,boolean framed,int direction,boolean middle) {
        this(design,detail,framed,direction,middle,false);
    }
    public SpaceDoorData(int design,int detail,boolean framed,int direction,boolean middle,boolean sliding) {
        this(design,detail,framed,direction,middle,sliding,true);
    }
    public SpaceDoorData(int design,int detail,boolean framed,int direction,boolean middle,boolean sliding,boolean hinges) {
        this(design,detail,framed,direction,middle,sliding,hinges,TRIGGER_DISABLED);
    }
    public SpaceDoorData(int design,int detail,boolean framed,int direction,boolean middle,boolean sliding,boolean hinges,int trigger) {
        this(design,detail,framed,direction,middle,sliding,hinges,trigger,true);
    }
    public SpaceDoorData(int design,int detail,boolean framed,int direction,boolean middle,boolean sliding,boolean hinges,int trigger,boolean panel) {
        this.design=design>=0 && design<15?design:2;
        this.detail=detail>=0 && detail<3?detail:1;
        this.framed=framed;
        this.direction=direction>=0 && direction<3?direction:0;
        this.middle=middle;
        this.sliding=sliding;
        this.hinges=hinges;
        this.panel=panel;
        this.trigger=validTrigger(trigger)?trigger:TRIGGER_DISABLED;
    }
    public static boolean validTrigger(int trigger) { return trigger>=TRIGGER_DISABLED && trigger<=TRIGGER_REDSTONE_OFF; }
    public static boolean openForSignal(int trigger,boolean powered) {
        return trigger==TRIGGER_REDSTONE_ON?powered:!powered;
    }
    public static double verticalTravel(boolean framed,int direction) {
        // Framed leaves retract the extra pixel into the border; bare leaves
        // retain their one-pixel reveal. Different leaf heights yield the same travel.
        return direction==0?0:31/16.0*(direction==2?-1:1);
    }
    /** Rotating art is edge-native; sliding art is centre-native. */
    public static double positionOffset(boolean sliding,boolean middle) {
        double centre=-5.24/16.0;
        return sliding?(middle?0:-centre):(middle?centre:0);
    }
    public void write(PrimitiveData data) {
        data.putInt("SpaceDesign",design); data.putInt("SpaceDetail",detail);
        data.putBoolean("SpaceFramed",framed); data.putInt("SpaceSlideDirection",direction);
        data.putBoolean("SpaceDoorMiddle",middle);
        data.putBoolean("SpaceDoorSliding",sliding); data.putInt("SpaceDoorSchema",2);
        data.putBoolean("SpaceDoorHinges",hinges);
        data.putBoolean("SpaceDoorPanel",panel);
        data.putInt("SpaceDoorTrigger",trigger);
    }
    public static SpaceDoorData read(PrimitiveData data) {
        return new SpaceDoorData(data.contains("SpaceDesign")?data.getInt("SpaceDesign"):2,
                data.contains("SpaceDetail")?data.getInt("SpaceDetail"):1,
                !data.contains("SpaceFramed") || data.getBoolean("SpaceFramed"),data.getInt("SpaceSlideDirection"),
                data.getBoolean("SpaceDoorMiddle"),data.getBoolean("SpaceDoorSliding"),
                !data.contains("SpaceDoorHinges") || data.getBoolean("SpaceDoorHinges"),
                data.getInt("SpaceDoorTrigger"),
                !data.contains("SpaceDoorPanel") || data.getBoolean("SpaceDoorPanel"));
    }
}
