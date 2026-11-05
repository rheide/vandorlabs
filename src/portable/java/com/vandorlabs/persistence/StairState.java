package com.vandorlabs.persistence;

import com.vandorlabs.ramp.ControllerPlatform;

/** Current stair tessellation save policy; geometry and editor interactions stay host-owned. */
public final class StairState {
    public static final String TREAD="StairTread",ROLL="StairRoll";
    public final int tread,roll;
    public StairState(int tread,int roll){this.tread=tread;this.roll=roll;}
    public static boolean valid(int tread,int roll){return ControllerPlatform.validTreadPixels(tread)&&roll>=0&&roll<=2;}
    /** Only an actual int selects a tread; missing or malformed values reset to8 pixels. */
    public static int readTread(boolean typedInt,int saved){return typedInt&&ControllerPlatform.validTreadPixels(saved)?saved:8;}
    /** Hosts retain their numeric-tag conversion before this clamp, including absent/wrong-type zero. */
    public static int readRoll(int saved){return Math.max(0,Math.min(2,saved));}
    public static StairState read(HousingTags tag){return new StairState(readTread(tag.containsInt(TREAD),tag.getInt(TREAD)),readRoll(tag.getInt(ROLL)));}
    public static int packed(int tread,int roll){return tread|(roll<<5);}
    public int packed(){return packed(tread,roll);}
    public void write(PrimitiveData tag){write(tag,tread,roll);}
    /** Scalar hot writer: no state allocation and no normalization of caller-owned values. */
    public static void write(PrimitiveData tag,int tread,int roll){tag.putInt(TREAD,tread);tag.putInt(ROLL,roll);}
}
