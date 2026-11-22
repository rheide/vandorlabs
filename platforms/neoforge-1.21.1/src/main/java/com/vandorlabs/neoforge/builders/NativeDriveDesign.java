package com.vandorlabs.neoforge.builders;

import java.util.List;

/** Standalone drive structures use the existing Constructor ownership journal. */
public enum NativeDriveDesign {
    LONGBURN("longburn_drive", "longburn_drive_core", 3, 3, 4, true, .5625, 1.375, .686875, .5, .625),
    MERIDIAN("meridian_core", "meridian_drive_core", 3, 3, 3, false, .375, .625, .186875, .5, .625),
    OCULUS("oculus_drive", "oculus_drive_core", 3, 3, 3, false, .8125, .65625, .364375, .375, .375),
    VECTOR_FOLD("vector_fold_array", "vector_fold_array_core", 3, 2, 3, false, .4375, .8125, .186875, .5, .625),
    PALADYNE_COMPUTER("paladyne_navigation_computer", "paladyne_navigation_core", 1, 3, 1, false, 0, 0, 0, 0, 0),
    SEYR_COMPUTER("seyr_navigation_computer", "seyr_navigation_core", 1, 3, 1, false, 0, 0, 0, 0, 0);

    public final String id, block;
    public final int width, height, depth;
    public final boolean exhaust;
    public final double chipX, chipY, chipZ, chipWidth, chipHeight;

    NativeDriveDesign(String id, String block, int width, int height, int depth, boolean exhaust, double chipX, double chipY, double chipZ, double chipWidth, double chipHeight) {
        this.id = id;
        this.block = block;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.exhaust = exhaust;
        this.chipX = chipX;this.chipY = chipY;this.chipZ = chipZ;
        this.chipWidth = chipWidth;this.chipHeight = chipHeight;
    }

    public boolean computer(){return this==PALADYNE_COMPUTER||this==SEYR_COMPUTER;}
    public boolean engine(){return this==LONGBURN||this==MERIDIAN||this==OCULUS;}
    public boolean hasChip(){return this!=LONGBURN&&!computer();}
    boolean savesChip() { return hasChip() || this == LONGBURN; }
    public boolean manualActivity(){return this==OCULUS||computer();}
    public int defaultActivity(){return computer()?1:2;}
    String activityLabel(int activity){
        return computer()?switch(activity){case 0->"Off";case 1->"Idle";default->"Computing";}:
                switch(activity){case 0->"Standby";case 1->"Charging";default->"Active";};
    }
    boolean accepts(NativeDriveSettings settings){
        return (exhaust||settings.particles()==0)&&(engine()||!settings.inverted()&&!settings.levels()&&settings.threshold()==8)
                &&(!computer()||settings.keyboard()==-2);
    }

    public static NativeDriveDesign of(String id) {
        return switch (id) {
            case "longburn_drive" -> LONGBURN;
            case "meridian_core" -> MERIDIAN;
            case "oculus_drive" -> OCULUS;
            case "vector_fold_array" -> VECTOR_FOLD;
            case "paladyne_navigation_computer" -> PALADYNE_COMPUTER;
            case "seyr_navigation_computer" -> SEYR_COMPUTER;
            default -> null;
        };
    }

    public static final List<NativeDriveDesign> SUPPORTED = List.of(LONGBURN, MERIDIAN, OCULUS, VECTOR_FOLD, PALADYNE_COMPUTER, SEYR_COMPUTER);
}
