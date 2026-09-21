package com.vandorlabs.animation;

/** Version-neutral global animation phase and vertical atlas coordinates. */
public final class AnimationFrames {
    private AnimationFrames() { }
    public static int frame(long tick,int ticksPerFrame,int frameCount) {
        if (frameCount<1) throw new IllegalArgumentException("frameCount must be positive");
        return (int)((tick/Math.max(1,ticksPerFrame))%frameCount);
    }
    public static double top(int frame,int frameCount) { return (double)frame/frameCount; }
    public static double bottom(int frame,int frameCount) { return (double)(frame+1)/frameCount; }
}
