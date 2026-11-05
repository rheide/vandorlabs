package com.vandorlabs.persistence;

/** Exact current four-detent arithmetic and scalar save defaults; transitions stay with the host. */
public final class SignalControlData {
    public static final String LOW="LowLimit",HIGH="HighLimit",STEP="ControlStep";
    private SignalControlData(){}
    public static boolean validLimits(int low,int high){return low>=1&&high<=15&&high-low>=2;}
    /** Preserve the public legacy fallback: every value other than 0, 1 or 2 selects high. */
    public static int levelForStep(int value,int low,int high){return value==0?0:value==1?low:value==2?(low+high+1)/2:high;}
    /** Positive external levels never select Off; equal distances retain the first (lower) detent. */
    public static int stepForLevel(int level,int low,int high){int nearest=0,distance=16;for(int i=level>0?1:0;i<4;i++){int d=Math.abs(level-levelForStep(i,low,high));if(d<distance){nearest=i;distance=d;}}return nearest;}
    /** Presence means any tag type; numeric conversion and invalid-pair prior-state retention are host-owned. */
    public static int savedLow(boolean present,int raw){return present?raw:5;}
    public static int savedHigh(boolean present,int raw){return present?raw:15;}
    public static int savedStep(int raw){return Math.max(0,Math.min(3,raw));}
}
