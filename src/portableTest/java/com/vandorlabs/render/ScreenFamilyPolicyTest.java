package com.vandorlabs.render;

public final class ScreenFamilyPolicyTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void near(double actual, double expected, String message) { require(Math.abs(actual - expected) < 1e-9, message); }
    private static ScreenSurface.Quad surface(ScreenFamilyPolicy.Kind kind, int slot, boolean upper, boolean inverted) {
        return ScreenFamilyPolicy.surface(kind, slot, false, upper, inverted, false, 0, false, 1);
    }
    public static void main(String[] args) {
        ScreenFamilyPolicy.Kind[] families = ScreenFamilyPolicy.Kind.values();
        int[] slots = {1, 1, 2, 2, 1, 1, 1}, heights = {128, 128, 128, 64, 128, 64, 64};
        for (int i = 0; i < families.length; i++) {
            ScreenFamilyPolicy.Kind kind = families[i];
            require(ScreenFamilyPolicy.supportsSlot(kind, 0) && ScreenFamilyPolicy.supportsSlot(kind, 1) == (slots[i] == 2)
                    && !ScreenFamilyPolicy.supportsSlot(kind, -1) && !ScreenFamilyPolicy.supportsSlot(kind, 2), "slot support " + kind);
            require(ScreenFamilyPolicy.displayHeight(kind, 0) == heights[i] && ScreenFamilyPolicy.maxRows(kind, 0) == heights[i] / 16
                    && ScreenFamilyPolicy.rowTop(kind, 0) == (heights[i] == 64 ? 8 : 24), "capacity and authored pixels " + kind);
        }
        require(!ScreenFamilyPolicy.supportsSlot(null, 0), "unsupported block family");
        require(surface(ScreenFamilyPolicy.Kind.FLAT, 0, false, false) == ScreenSurface.quad(ScreenSurface.Kind.FLAT, false), "flat face identity");
        require(surface(ScreenFamilyPolicy.Kind.DIAGONAL, 0, false, true) == ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL, true), "inverted diagonal face identity");
        require(surface(ScreenFamilyPolicy.Kind.CONSOLE, 0, false, false) == ScreenSurface.quad(ScreenSurface.Kind.CONSOLE, false)
                && surface(ScreenFamilyPolicy.Kind.CONSOLE, 1, false, false) == ScreenSurface.input(InputSurfaceLayout.halfConsoleFront()), "console screen/deck slots");
        require(surface(ScreenFamilyPolicy.Kind.HALF_CONSOLE, 0, false, false) == ScreenSurface.input(InputSurfaceLayout.halfConsoleFront())
                && surface(ScreenFamilyPolicy.Kind.HALF_CONSOLE, 1, false, false) == ScreenSurface.input(InputSurfaceLayout.halfConsoleRear()), "half-console bottom/top slots");
        require(ScreenFamilyPolicy.maxRows(ScreenFamilyPolicy.Kind.CONSOLE, 1) == 4, "console deck half-height capacity");
        near(surface(ScreenFamilyPolicy.Kind.DIAGONAL_HALF, 0, true, false).topRight.y, 13.875, "upper half incline plane");
        near(surface(ScreenFamilyPolicy.Kind.DIAGONAL_HALF, 0, false, false).bottomRight.y, 2.125, "lower half incline plane");
        InputSurfaceLayout.Mounted small = ScreenFamilyPolicy.input(ScreenFamilyPolicy.Kind.HALF_INPUT, false, false, false, 1, true, 2);
        near(small.housing.x1 - small.housing.x0, 11.2, "small input authored width");
        near(small.housing.y1 - small.housing.y0, 5.6, "small input authored height");
        near(small.housing.y0, 5.2, "middle wall slot");
        for (int slot = 0; slot < 3; slot++) {
            InputSurfaceLayout.Mounted ceiling = ScreenFamilyPolicy.input(ScreenFamilyPolicy.Kind.FULL_INPUT, false, false, true, 0, true, slot);
            near(ceiling.housing.z0, (slot - 1) * 4, "full ceiling overhang slots");
            near(ceiling.housing.z1 - ceiling.housing.z0, 16, "full input ignores small flag");
            require(ceiling.surface.ny < -.999, "ceiling artwork faces down");
        }
        int placements = 0;
        for (ScreenFamilyPolicy.Kind kind : families) for (int face = 0; face < 6; face++) for (float hit : new float[]{0, .5F, .5001F, 1}) {
            for (boolean sneak : new boolean[]{false, true}) for (Boolean support : new Boolean[]{null, false, true}) {
                ScreenFamilyPolicy.Placement placed = ScreenFamilyPolicy.placement(kind, face, hit, sneak, 4, support);
                boolean mounted = ScreenFamilyPolicy.input(kind) || kind == ScreenFamilyPolicy.Kind.DIAGONAL_HALF;
                require(placed.facing == (mounted && face >= 2 ? face : 4), "mounted support versus player-facing placement");
                boolean upper = face == 0 || face >= 2 && hit > .5F;
                if (mounted && face >= 2 && support != null) upper = support;
                require(placed.upper == (mounted && upper), "slab support half override");
                require(placed.keyboard == (ScreenFamilyPolicy.input(kind) && (face < 2 || sneak)), "floor/ceiling and sneak keyboard placement");
                require(placed.inverted == (kind == ScreenFamilyPolicy.Kind.DIAGONAL && (face == 0 || face >= 2 && hit > .5F)), "stair-style inversion");
                placements++;
            }
        }
        require(ScreenFamilyPolicy.inheritedFacing(0, 5) == 0 && ScreenFamilyPolicy.inheritedFacing(-1, 5) == 4, "exact adjacent block inheritance");
        require(ScreenFamilyPolicy.towardView(2, 45) == 3 && ScreenFamilyPolicy.towardView(2, -45) == 3
                && ScreenFamilyPolicy.towardView(2, 45.01F) == 1 && ScreenFamilyPolicy.towardView(2, -45.01F) == 0, "strict vertical view thresholds");
        require(ScreenFamilyPolicy.rotateY(2, 1) == 5 && ScreenFamilyPolicy.rotateY(2, -1) == 4, "horizontal clockwise rotation");
        for (int face = 0; face < 6; face++) for (int turns = -8; turns <= 8; turns++) {
            require(ScreenFamilyPolicy.rotateY(ScreenFamilyPolicy.rotateY(face, turns), -turns) == face, "six-face rotation reversibility");
        }
        System.out.println("Screen family policy PASS: 7 authored families, slot/row limits, ceiling/small surfaces and " + placements + " placement fixtures");
    }
}
