package com.vandorlabs.render;

/** Fixed jamb control pad, measured in model pixels. */
public final class SpaceDoorControlPanel {
    public enum Side { LEFT, RIGHT, NONE }
    public static final double Y0=16.5, Y1=19.5;
    private SpaceDoorControlPanel() { }
    public static Side side(boolean hingeOnVisualLeft,boolean sliding,boolean paired,
            boolean neighborLeft,boolean neighborRight) {
        if (neighborLeft && neighborRight) return Side.NONE;
        if (sliding) {
            if (paired) return hingeOnVisualLeft?Side.NONE:Side.RIGHT;
            return hingeOnVisualLeft?Side.RIGHT:Side.LEFT;
        }
        if (paired && !hingeOnVisualLeft) return Side.NONE;
        return hingeOnVisualLeft?Side.LEFT:Side.RIGHT;
    }
    public static double x0(Side side) { return side==Side.RIGHT?15:0; }
    public static double x1(Side side) { return side==Side.RIGHT?16:1; }
    // Both models face local +Z. Mount the pad against the rear of the jamb,
    // clear of the moving leaf and within the placed block after offsets.
    public static double z0(boolean sliding) { return sliding?3:8.24; }
    public static double z1(boolean sliding) { return z0(sliding)+3; }
    /** Only far-edge doors mount the pad on the opposite depth face of the jamb. */
    public static double z0(boolean sliding, boolean farEdge) {
        return farEdge ? (sliding ? 10 : 15.24) : z0(sliding);
    }
    public static double z1(boolean sliding, boolean farEdge) { return z0(sliding,farEdge)+3; }
    public static boolean contains(double localZ,double localY,boolean sliding) {
        return contains(localZ,localY,sliding,false);
    }
    public static boolean contains(double localZ,double localY,boolean sliding,boolean farEdge) {
        return localZ>=z0(sliding,farEdge) && localZ<=z1(sliding,farEdge)
                && localY>=Y0 && localY<=Y1;
    }
}
