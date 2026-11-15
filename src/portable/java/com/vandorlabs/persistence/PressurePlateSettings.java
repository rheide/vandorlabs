package com.vandorlabs.persistence;

/** Shared physical sensing and independent analogue output policy for programmable plates. */
public final class PressurePlateSettings {
    public static final String TEXTURE="PlateTexture",MODE="PlateMode",SENSITIVITY="PlateSensitivity",OUTPUT="PlateOutput";
    public final int texture,mode,sensitivity;
    public PressurePlateSettings(int texture,int mode,int sensitivity){
        if(texture<0||mode<0||mode>2||sensitivity<0||sensitivity>2)throw new IllegalArgumentException("Invalid pressure plate settings");
        this.texture=texture;this.mode=mode;this.sensitivity=sensitivity;
    }
    public static int level(int value){return Math.max(0,Math.min(15,value));}
    /** Native weighted plates count entities rather than the items in each stack. */
    public static int measure(int sensitivity,int entityCount,boolean woodenTriggerPresent){
        if(sensitivity==0)return woodenTriggerPresent?15:0;
        int maximum=sensitivity==1?15:150,count=Math.min(Math.max(0,entityCount),maximum);
        return count==0?0:(int)Math.ceil(((float)count/(float)maximum)*15F);
    }
    public static int output(int mode,int physical){int bounded=level(physical);return mode==1?bounded:mode==2&&bounded==0?15:0;}
    public static int releaseTicks(int sensitivity){return sensitivity==0?20:10;}
    public static int savedMode(boolean present,int value){return present?Math.max(0,Math.min(2,value)):1;}
    public static int savedSensitivity(int value){return Math.max(0,Math.min(2,value));}
    public void write(PrimitiveData tag){tag.putInt(TEXTURE,texture);tag.putInt(MODE,mode);tag.putInt(SENSITIVITY,sensitivity);}
}
