package com.vandorlabs.fluid;

/** Ordinary/segmented door outlet decisions. Native world access remains in each loader adapter. */
public final class DoorWaterOutlets {
    private DoorWaterOutlets() {}
    public static final int NONE=0, RETAIN=1, PUBLISH=3, DRAIN=4;
    /** Resolved fluid presence, not raw opaque slot occupancy, matches old saved doors. */
    public static int wetSide(boolean firstWater,boolean secondWater,boolean firstPresent,boolean secondPresent){
        int wet=firstWater?0:secondWater?1:-1;
        return wet<0||(wet==0?secondPresent:firstPresent)?-1:wet;
    }
    /** Existing natural sources are never acquired. Flowing water and replaceable dry cells may be fed. */
    public static int supply(boolean previous,boolean loaded,boolean water,boolean source,boolean liquid,boolean replaceable){
        if(!loaded||water&&source)return previous?RETAIN:NONE;
        return !water&&(liquid||!replaceable)?NONE:PUBLISH;
    }
    /** Ordinary destroyed owners resolve their boundary source immediately; active owners defer unloaded cells. */
    public static int retire(boolean previous,boolean retained,boolean loaded,boolean stop,boolean water,boolean source){
        if(!previous||retained)return NONE;
        if(!loaded&&!stop)return RETAIN;
        return water&&source?DRAIN:NONE;
    }
}
