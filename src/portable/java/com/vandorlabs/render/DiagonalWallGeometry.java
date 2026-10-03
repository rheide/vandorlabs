package com.vandorlabs.render;

/** Block-grid slopes shared by walls, portholes and moving leaves. */
public final class DiagonalWallGeometry {
    private DiagonalWallGeometry() { }
    public static double span(int mode) { return mode == 1 ? 1 : .5; }
    public static double band(int mode, boolean inverted) {
        return mode == 2 && inverted ? .5 : 0;
    }
    /** Near surface before the shallow panel swaps its Y and Z axes. */
    public static double near(int mode, boolean inverted, double along) {
        return (inverted ? 1 - along : along) * span(mode) - .125;
    }
    /** Four-pixel end transitions meet existing flat panel positions. */
    public static double near(int mode, boolean inverted, double along,
            double lower, double upper) {
        double near = near(mode, inverted, along);
        if (!Double.isNaN(lower) && along < .25)
            near += (lower - near(mode, inverted, 0)) * (1 - along * 4);
        if (!Double.isNaN(upper) && along > .75)
            near += (upper - near(mode, inverted, 1)) * (along * 4 - 3);
        return near;
    }
}
