package com.vandorlabs.persistence;

/** Source chair style, height and eight-angle settings; platform ownership is adapter-owned. */
public final class ChairSettings {
    public static final String STYLE="ChairStyle",HEIGHT="ChairHeight",ANGLE="ChairAngle",JOIN="SeatJoin",PILOT_HEIGHT="PilotHeight";
    private static final double[] CUSHIONS={12D/16,11D/16,12D/16,11D/16,10D/16};
    private static final double[] BACKS={24D/16,21D/16,23D/16,23.5D/16,18.5D/16};
    public final int style,height,angle;
    public final boolean join;
    public ChairSettings(int style,int height,int angle,boolean join){if(!valid(style,height,angle))throw new IllegalArgumentException("Invalid chair settings");this.style=style;this.height=height;this.angle=angle;this.join=join;}
    public static boolean valid(int style,int height,int angle){return style>=0&&style<5&&height>=0&&height<3&&angle>=0&&angle<8;}
    public static int style(int value){return Math.max(0,Math.min(4,value));}
    public static int height(boolean present,int value){return present?Math.max(0,Math.min(2,value)):1;}
    public static int angle(int value){return Math.max(0,Math.min(7,value));}
    public static int heightOffsetPixels(int height,boolean connected){return (height-1)*2+(connected?1:0);}
    public static double cushion(int style,int height){return CUSHIONS[style]+heightOffsetPixels(height,false)/16D;}
    public static double back(int style,int height){return BACKS[style]+heightOffsetPixels(height,false)/16D;}
    public static double connectedCushion(boolean luxury,int height){return (luxury?9:8)/16D+heightOffsetPixels(height,true)/16D;}
    public static int pilotHeight(int value){return Math.max(0,Math.min(3,value));}
    public static double pilotCushion(int height){return 7D/16+height/8D;}
    /** 0 single, 1 left, 2 middle, 3 right: matches the Source authored model names. */
    public static int part(boolean ownJoin,boolean left,boolean right){return !ownJoin?0:left?(right?2:3):(right?1:0);}
    public static boolean connects(boolean loaded,boolean sameKind,boolean lower,boolean sameFacing,int ownHeight,int otherHeight,boolean otherJoin){return loaded&&sameKind&&lower&&sameFacing&&ownHeight==otherHeight&&otherJoin;}
}
