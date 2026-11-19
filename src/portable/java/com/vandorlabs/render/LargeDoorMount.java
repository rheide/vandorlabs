package com.vandorlabs.render;

/** Coordinate transforms for a 3×3 door mounted along the edge of a floor opening. */
public final class LargeDoorMount {
    private LargeDoorMount() {}

    public static int rowX(int facing) { return facing == 3 ? 1 : facing == 1 ? -1 : 0; }
    public static int rowZ(int facing) { return facing == 0 ? 1 : facing == 2 ? -1 : 0; }

    public static double row(int facing, double x, double z) {
        return rowX(facing) == 0 ? (rowZ(facing) > 0 ? z : 1-z) : (rowX(facing) > 0 ? x : 1-x);
    }

    /** Rotate an already oriented upright box around the width axis, within its anchor cell. */
    public static <T> T horizontal(SpaceDoorBounds.Factory<T> box, int facing,
            double x0, double y0, double z0, double x1, double y1, double z1) {
        int x = rowX(facing);
        int z = rowZ(facing);
        double low = Math.min(row(facing,x0,z0),row(facing,x1,z1));
        double high = Math.max(row(facing,x0,z0),row(facing,x1,z1));
        return box.create(x == 0 ? x0 : x > 0 ? y0 : 1-y1, 1-high, z == 0 ? z0 : z > 0 ? y0 : 1-y1,
                x == 0 ? x1 : x > 0 ? y1 : 1-y0, 1-low, z == 0 ? z1 : z > 0 ? y1 : 1-y0);
    }
}
