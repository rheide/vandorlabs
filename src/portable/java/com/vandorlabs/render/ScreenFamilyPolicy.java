package com.vandorlabs.render;

/** Authored surfaces, display capacity and placement decisions shared by programmable families. */
public final class ScreenFamilyPolicy {
    public enum Kind { FLAT, DIAGONAL, CONSOLE, HALF_CONSOLE, FULL_INPUT, HALF_INPUT, DIAGONAL_HALF }
    private ScreenFamilyPolicy() { }
    public static boolean supportsSlot(Kind kind, int slot) {
        return kind != null && (slot == 0 || slot == 1 && (kind == Kind.CONSOLE || kind == Kind.HALF_CONSOLE));
    }
    public static boolean half(Kind kind, int slot) {
        return kind == Kind.HALF_INPUT || kind == Kind.DIAGONAL_HALF || kind == Kind.HALF_CONSOLE || kind == Kind.CONSOLE && slot == 1;
    }
    public static int displayHeight(Kind kind, int slot) { return half(kind, slot) ? 64 : 128; }
    public static int rowTop(Kind kind, int slot) { return half(kind, slot) ? 8 : 24; }
    public static int maxRows(Kind kind, int slot) { return half(kind, slot) ? 4 : 8; }
    public static boolean input(Kind kind) { return kind == Kind.FULL_INPUT || kind == Kind.HALF_INPUT; }
    public static InputSurfaceLayout.Mounted input(Kind kind, boolean keyboard, boolean upper, boolean ceiling, int wallPosition, boolean small, int ceilingPosition) {
        if (!input(kind)) throw new IllegalArgumentException("Input family required");
        boolean full = kind == Kind.FULL_INPUT;
        if (ceiling) return InputSurfaceLayout.ceilingInput(full, !full && small, ceilingPosition);
        return full ? InputSurfaceLayout.fullInput(keyboard, upper) : InputSurfaceLayout.halfInput(keyboard, upper, wallPosition, small);
    }
    public static ScreenSurface.Quad surface(Kind kind, int slot, boolean keyboard, boolean upper, boolean inverted,
            boolean ceiling, int wallPosition, boolean small, int ceilingPosition) {
        if (input(kind)) return ScreenSurface.input(input(kind, keyboard, upper, ceiling, wallPosition, small, ceilingPosition).surface);
        if (kind == Kind.DIAGONAL_HALF) return ScreenSurface.halfDiagonal(upper);
        if (kind == Kind.CONSOLE) return slot == 1 ? ScreenSurface.input(InputSurfaceLayout.halfConsoleFront()) : ScreenSurface.quad(ScreenSurface.Kind.CONSOLE, false);
        if (kind == Kind.HALF_CONSOLE) return ScreenSurface.input(slot == 1 ? InputSurfaceLayout.halfConsoleRear() : InputSurfaceLayout.halfConsoleFront());
        return ScreenSurface.quad(kind == Kind.DIAGONAL ? ScreenSurface.Kind.DIAGONAL : ScreenSurface.Kind.FLAT, kind == Kind.DIAGONAL && inverted);
    }
    public static int opposite(int facing) {
        if (facing < 0 || facing > 5) throw new IllegalArgumentException("Facing index");
        return facing ^ 1;
    }
    public static int towardPlayer(int horizontalFacing) { return opposite(horizontalFacing); }
    public static int towardView(int horizontalFacing, float pitch) { return pitch > 45 ? 1 : pitch < -45 ? 0 : towardPlayer(horizontalFacing); }
    /** The host supplies an inherited face only when the adjacent block is the exact same registered block. */
    public static int inheritedFacing(int adjacentSameBlockFacing, int horizontalFacing) { return adjacentSameBlockFacing >= 0 ? adjacentSameBlockFacing : towardPlayer(horizontalFacing); }
    public static int rotateY(int facing, int quarters) {
        if (facing < 0 || facing > 5) throw new IllegalArgumentException("Facing index");
        if (facing < 2) return facing;
        int[] cycle = {2, 5, 3, 4}; int index = 0;
        while (cycle[index] != facing) index++;
        return cycle[Math.floorMod(index + quarters, 4)];
    }
    public static final class Placement {
        public final int facing;
        public final boolean keyboard, upper, inverted;
        private Placement(int facing, boolean keyboard, boolean upper, boolean inverted) { this.facing = facing; this.keyboard = keyboard; this.upper = upper; this.inverted = inverted; }
    }
    /** World lookup stays with the host; a side slab's own half overrides the hit half for mounted input families. */
    public static Placement placement(Kind kind, int clickedFace, float hitY, boolean sneaking, int defaultFacing, Boolean supportUpper) {
        boolean side = clickedFace >= 2;
        boolean upper = clickedFace == 0 || side && hitY > .5F;
        if (input(kind) || kind == Kind.DIAGONAL_HALF) {
            if (side && supportUpper != null) upper = supportUpper;
            return new Placement(side ? clickedFace : defaultFacing, input(kind) && (!side || sneaking), upper, false);
        }
        return new Placement(defaultFacing, false, false, kind == Kind.DIAGONAL && upper);
    }
}
