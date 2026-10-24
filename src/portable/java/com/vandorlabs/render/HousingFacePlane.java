package com.vandorlabs.render;

/** Immutable joined texture-plane bounds relative to one block. */
public class HousingFacePlane {
    private final int[] min, size;
    public HousingFacePlane(int[] min, int[] size) { this.min = min.clone(); this.size = size.clone(); }
    public final int min(int axis) { return min[axis]; }
    public final int size(int axis) { return size[axis]; }
    @Override public String toString() { return java.util.Arrays.toString(min) + java.util.Arrays.toString(size); }
}
