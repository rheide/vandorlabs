package com.vandorlabs.appliance;

/** Authored appliance identities; face order follows DOWN, UP, NORTH, SOUTH, WEST, EAST. */
public final class ApplianceCatalog {
    public static final String[] FACES={"bottom","top","front","back","left","right"};
    public static final String[] IDS={"indoor_ac_slab_dark_cyan","indoor_ac_slab_silver","outdoor_ac_dark_cyan","outdoor_ac_silver","printer","scanner"};
    public static final String[] LABELS={"Indoor AC (Dark Cyan)","Indoor AC (Silver)","Outdoor AC (Dark Cyan)","Outdoor AC (Silver)","Printer","Scanner"};
    private ApplianceCatalog(){}
    public static boolean preset(int index){return index>=0&&index<4;}
    public static boolean slab(int index){return index>=0&&index<2;}
    public static String texture(int index,int face){return "appliances/"+IDS[index]+"/"+FACES[face];}
}
