package com.vandorlabs.persistence;

import com.vandorlabs.ramp.ControllerPlatform;

/** Saved stair tessellation settings, including the legacy third roll and 16px tread. */
public final class StairState {
    public final int tread,roll;
    public StairState(int tread,int roll){this.tread=tread;this.roll=roll;}
    public static boolean valid(int tread,int roll){return ControllerPlatform.validTreadPixels(tread)&&roll>=0&&roll<=2;}
    public int packed(){return tread|(roll<<5);}
    public static StairState read(PrimitiveData tag){int tread=tag.containsInt("StairTread")?tag.getInt("StairTread"):8;return new StairState(ControllerPlatform.validTreadPixels(tread)?tread:8,Math.max(0,Math.min(2,tag.getInt("StairRoll"))));}
    public void write(PrimitiveData tag){tag.putInt("StairTread",tread);tag.putInt("StairRoll",roll);}
    public int sliderIndex(){return Math.min(3,Integer.numberOfTrailingZeros(tread));}
    public StairState slider(int index){return new StairState(1<<index,roll);}
    /** The editor exposes two poses; saved roll2 switches to Normal too. */
    public StairState toggleRoll(){return new StairState(tread,roll==0?1:0);}
}
