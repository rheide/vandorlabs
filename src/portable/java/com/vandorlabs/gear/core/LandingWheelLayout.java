package com.vandorlabs.gear.core;

/** Released horizontal metadata and root-relative footprint of standalone side wheels. */
public final class LandingWheelLayout {
    private static void check(int facing,int part){if(facing<0||facing>3||part<0||part>3)throw new IllegalArgumentException("Invalid wheel cell");}
    public static int x(int facing,int part){check(facing,part);return (part&1)*(facing==0?-1:facing==2?1:0);}
    public static int y(int part){check(0,part);return part>>1;}
    public static int z(int facing,int part){check(facing,part);return (part&1)*(facing==1?-1:facing==3?1:0);}
    private LandingWheelLayout(){}
}
