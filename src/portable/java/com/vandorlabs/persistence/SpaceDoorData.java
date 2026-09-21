package com.vandorlabs.persistence;

/** Stable appearance indices: append designs, never reorder existing entries. */
public final class SpaceDoorData {
    public final int design,detail,direction;
    public final boolean framed;
    public final boolean middle;
    public SpaceDoorData(int design,int detail,boolean framed,int direction) {
        this(design,detail,framed,direction,false);
    }
    public SpaceDoorData(int design,int detail,boolean framed,int direction,boolean middle) {
        this.design=design>=0 && design<15?design:2;
        this.detail=detail>=0 && detail<3?detail:1;
        this.framed=framed;
        this.direction=direction>=0 && direction<3?direction:0;
        this.middle=middle;
    }
    public static double verticalTravel(boolean framed,int direction) {
        // Framed leaves retract the extra pixel into the border; bare leaves
        // retain their one-pixel reveal. Different leaf heights yield the same travel.
        return direction==0?0:31/16.0*(direction==2?-1:1);
    }
    public void write(PrimitiveData data) {
        data.putInt("SpaceDesign",design); data.putInt("SpaceDetail",detail);
        data.putBoolean("SpaceFramed",framed); data.putInt("SpaceSlideDirection",direction);
        data.putBoolean("SpaceDoorMiddle",middle);
    }
    public static SpaceDoorData read(PrimitiveData data) {
        return new SpaceDoorData(data.contains("SpaceDesign")?data.getInt("SpaceDesign"):2,
                data.contains("SpaceDetail")?data.getInt("SpaceDetail"):1,
                !data.contains("SpaceFramed") || data.getBoolean("SpaceFramed"),data.getInt("SpaceSlideDirection"),
                data.getBoolean("SpaceDoorMiddle"));
    }
}
