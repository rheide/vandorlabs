package com.vandorlabs.gear.track.core;

/** Model-space mounting contract; support inspection itself belongs to the Forge adapter. */
public final class TrackMounting {
    public static final double MODEL_UNITS_PER_BLOCK = 16.0;
    public static final double SIDE_BRACKET_MIN_Y = 13.0 / 16.0;
    public static final double SIDE_BRACKET_MAX_Y = 15.0 / 16.0;
    public static final double ARM_SOCKET_Y = 1.0;

    private TrackMounting() { }

    /** Ordinary overhead mounting is not restricted to full cubes or solid underside faces. */
    public static boolean acceptsOverhead(boolean isAir) { return !isAir; }

    /**
     * The special sideways case is a single top-half slab in the neighboring cell.
     * A bottom slab misses the bracket height; a double slab is not this special case.
     */
    public static boolean acceptsSlabSide(boolean isSlab, boolean isDouble, boolean isTopHalf) {
        return isSlab && !isDouble && isTopHalf;
    }
}
