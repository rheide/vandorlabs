package com.vandorlabs.animation;

/** Display-mode and playback rules without world, block-entity, or GUI types. */
public final class ScreenBehavior {
    public static final int OFF=0,STATIC=1,ANIMATED=2;
    private static final int[] SPEED_TICKS={20,10,5};
    private ScreenBehavior() { }

    public static int clampMode(int mode) { return Math.max(OFF,Math.min(ANIMATED,mode)); }
    public static int clampSpeedIndex(int index) { return Math.max(0,Math.min(SPEED_TICKS.length-1,index)); }
    public static int animationTicks(int speedIndex) { return SPEED_TICKS[clampSpeedIndex(speedIndex)]; }
    public static int effectiveMode(int configured,boolean redstoneEnabled,boolean powered) {
        int mode=clampMode(configured);
        if (redstoneEnabled&&!powered) return OFF;
        if (mode==OFF) return powered?ANIMATED:OFF;
        return mode;
    }
}
