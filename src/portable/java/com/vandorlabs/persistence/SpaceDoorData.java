package com.vandorlabs.persistence;

/** Stable appearance indices: append designs, never reorder existing entries. */
public final class SpaceDoorData {
    public static final int TRIGGER_DISABLED=0, TRIGGER_REDSTONE_ON=1, TRIGGER_REDSTONE_OFF=2;
    public static final int X_SPLIT=3, VERTICAL_SPLIT=4;
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
        this.design=design>=0 && design<30?design:2;
        this.detail=detail>=0 && detail<3?Math.min(detail,1):1;
        this.framed=framed;
        this.direction=validDirection(direction,true)?direction:0;
        this.middle=middle;
        this.sliding=sliding;
        this.hinges=hinges;
        this.panel=panel;
        this.trigger=validTrigger(trigger)?trigger:TRIGGER_DISABLED;
    }
    public static boolean validDirection(int direction,boolean large) {
        return direction>=0&&direction<=(large?VERTICAL_SPLIT:2);
    }
    public static boolean validTrigger(int trigger) { return trigger>=TRIGGER_DISABLED && trigger<=TRIGGER_REDSTONE_OFF; }
    public static boolean openForSignal(int trigger,boolean powered) {
        return trigger==TRIGGER_REDSTONE_ON?powered:!powered;
    }
    public static double verticalTravel(boolean framed,int direction) {
        // Framed leaves retract the extra pixel into the border; bare leaves
        // retain their one-pixel reveal. Different leaf heights yield the same travel.
        return direction==1?31/16.0:direction==2?-31/16.0:0;
    }
    /** Framed models meet the block edge; bare models keep one pixel of clearance. */
    public static double positionOffset(boolean sliding,boolean framed,boolean hinges,int depth) {
        if (sliding) {
            double edge=6/16.0;
            return depth==0?0:depth==2?-edge:edge;
        }
        if (depth==0) return -5.24/16.0;
        return (depth==2?-(hinges?10.49:framed?11.24:12.24)+(framed?0:1):0.76)/16.0;
    }
    public void write(PrimitiveData data) {
        data.putInt("SpaceDesign",design); data.putInt("SpaceDetail",detail);
        data.putBoolean("SpaceFramed",framed); data.putInt("SpaceSlideDirection",direction);
        data.putBoolean("SpaceDoorMiddle",middle);
        data.putBoolean("SpaceDoorSliding",sliding); data.putInt("SpaceDoorSchema",direction==VERTICAL_SPLIT?3:2);
        data.putBoolean("SpaceDoorHinges",hinges);
        data.putBoolean("SpaceDoorPanel",panel);
        data.putInt("SpaceDoorTrigger",trigger);
    }
    public static SpaceDoorData read(PrimitiveData data) {
        int direction=data.getInt("SpaceSlideDirection");
        // Older snapshots used 4 as an invalid value. Only an authored split has schema 3.
        if(direction==VERTICAL_SPLIT&&data.getInt("SpaceDoorSchema")<3)direction=0;
        return new SpaceDoorData(data.contains("SpaceDesign")?data.getInt("SpaceDesign"):2,
                data.contains("SpaceDetail")?data.getInt("SpaceDetail"):1,
                !data.contains("SpaceFramed") || data.getBoolean("SpaceFramed"),direction,
                data.getBoolean("SpaceDoorMiddle"),data.getBoolean("SpaceDoorSliding"),
                !data.contains("SpaceDoorHinges") || data.getBoolean("SpaceDoorHinges"),
                data.getInt("SpaceDoorTrigger"),
                !data.contains("SpaceDoorPanel") || data.getBoolean("SpaceDoorPanel"));
    }
}
