package com.vandorlabs.surface.core;

/** Stable authored IDs and filenames, independent of either engine's material catalog. */
public final class SurfaceArtwork {
    public static final String[] IDS={"original_off","original_on","stone_iron","rotary_deck","basalt","graphite_ivory","landing","landing-grey","maintenance","maintenance-grey","docking","docking-grey","copier-input","copier-output"};
    public static final String[] LABELS={"Dormant","Active","Iron","Rotary","Basalt","Ivory","Landing","Landing Grey","Maintenance","Maintenance Grey","Docking","Docking Grey","Copier Input","Copier Output"};
    public static final String[] FILES={"turntable/turntable_block_top","turntable/turntable_block_top_on","turntable/turntable_block_top_stone_iron","turntable/turntable_block_top_rotary_deck","turntable/turntable_block_top_basalt","turntable/turntable_block_top_graphite_ivory","surface/landing","surface/landing-grey","surface/maintenance","surface/maintenance-grey","surface/docking","surface/docking-grey","surface/copier-input","surface/copier-output"};
    public static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return -1;}
    public static void checkMode(int mode){if(mode<0||mode>2)throw new IllegalArgumentException("Unknown Surface texture layout");}
    private SurfaceArtwork(){}
}
