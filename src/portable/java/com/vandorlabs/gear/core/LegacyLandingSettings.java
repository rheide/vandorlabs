package com.vandorlabs.gear.core;

/** Released telescopic control defaults and motion; no platform or Vehicle authority. */
public final class LegacyLandingSettings {
    public final int mode,pixels,size;
    public LegacyLandingSettings(int mode,int pixels,int size,boolean nonWheeled){if(!valid(mode,pixels,size,nonWheeled))throw new IllegalArgumentException("Invalid telescopic settings");this.mode=mode;this.pixels=pixels;this.size=size;}
    public static boolean valid(int mode,int pixels,int size,boolean nonWheeled){return mode>=0&&mode<=2&&pixels>=0&&pixels<=64&&pixels%8==0&&size>=0&&size<=4&&(!nonWheeled||size==0||size==4);}
    public static int mode(boolean present,int value){return present?Math.max(0,Math.min(2,value)):1;}
    public static int pixels(boolean present,int value){return present?Math.round(Math.max(0,Math.min(64,value))/8F)*8:16;}
    public static int size(int value){return Math.max(0,Math.min(4,value));}
    public static float progress(float value){return Float.isFinite(value)?Math.max(0,Math.min(4,value)):0;}
    public static float target(boolean extended,int pixels){return extended?pixels/16F:0;}
    public static float advance(float progress,float target){return progress<target?Math.min(target,progress+.05F):Math.max(target,progress-.05F);}
    /** Mode0 retains manual intent; channel/local OR is evaluated by the caller. */
    public static boolean automaticTarget(int mode,boolean signal){if(mode<1||mode>2)throw new IllegalArgumentException("Manual target has no automatic value");return mode==1?signal:!signal;}
    public static float pistonAnchor(int size){return (size==0?12:size==1?14:size==2?15:14)/16F;}
    public static float pistonLength(int size){return (size==0?2:size==1?1:size==2?.5F:1)/16F;}
}
