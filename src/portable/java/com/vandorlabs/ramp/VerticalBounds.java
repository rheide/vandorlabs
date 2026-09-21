package com.vandorlabs.ramp;

/** Version-neutral build-height range; adapters supply the game's minimum and height. */
public final class VerticalBounds {
    public final int minY,height;
    public VerticalBounds(int minY,int height) {
        if (height<1) throw new IllegalArgumentException("height must be positive");
        this.minY=minY; this.height=height;
    }
    public boolean contains(int y) { return y>=minY&&y<(long)minY+height; }
    public static VerticalBounds legacy(int height) { return new VerticalBounds(0,height); }
}
