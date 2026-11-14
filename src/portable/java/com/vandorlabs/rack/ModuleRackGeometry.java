package com.vandorlabs.rack;

/** Shared model-unit layout and front-face hit mapping for a four-slot rack.
 * The authored front faces SOUTH: its viewer-right control fascia is x=13..16.
 * No world, item, loader, or client classes are needed to test these contracts.
 */
public final class ModuleRackGeometry {
    public static final int SLOTS = 4;
    public static final int ALL_SLOTS = 15;
    public static final double RIM = 1.0;
    public static final double FASCIA_MIN_X = 13.0;
    public static final double FASCIA_DEPTH = 15.0;
    public static final double MODULE_MIN_X = 1.25;
    public static final double MODULE_MAX_X = 12.75;
    public static final double BUTTON_MIN_X = 13.5;
    public static final double BUTTON_MAX_X = 15.5;
    public static final double BUTTON_HALF_HEIGHT = 0.625;

    private ModuleRackGeometry() { }

    public static boolean validSlot(int slot) { return slot >= 0 && slot < SLOTS; }
    public static double slotMinY(int slot) { return 11.75 - 3.5 * slot; }
    public static double slotMaxY(int slot) { return slotMinY(slot) + 3.0; }
    public static double slotCenterY(int slot) { return slotMinY(slot) + 1.5; }

    /** The recessed fascia reaches the outer right edge, leaving its last depth
     * pixel open. Only the other four sides can completely occlude a neighbour.
     */
    public static boolean hasClosedBoundary(int facing, int side) {
        if (facing < 0 || facing > 5 || side < 0 || side > 5 || side == facing) return false;
        int right;
        switch (facing) {
            case 2: right = 4; break;
            case 4: right = 3; break;
            case 5: right = 2; break;
            default: right = 5; break;
        }
        return side != right;
    }

    /** Minecraft EnumFacing indexes: DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5. */
    public static double frontX(int facing, double x, double z) {
        switch (facing) {
            case 0:
            case 1:
            case 3: return x;
            case 2: return 1.0 - x;
            case 4: return z;
            case 5: return 1.0 - z;
            default: return Double.NaN;
        }
    }

    /** Face-local up: north on a floor-mounted rack, south on a ceiling-mounted rack. */
    public static double frontY(int facing, double y, double z) {
        switch (facing) {
            case 0: return z;
            case 1: return 1.0 - z;
            case 2:
            case 3:
            case 4:
            case 5: return y;
            default: return Double.NaN;
        }
    }

    /** Returns slot 0 at the top, or -1 for a rim, separator, non-button panel, or rear hit.
     * Coordinates are normalized block hit coordinates, not model units.
     */
    public static int hitSlot(int facing, int clickedFace, double x, double y, double z) {
        if (facing != clickedFace || !unit(x) || !unit(y) || !unit(z)) return -1;
        double u = 16.0 * frontX(facing, x, z), v = 16.0 * frontY(facing, y, z);
        if (Double.isNaN(u)) return -1;
        if (u >= RIM && u < FASCIA_MIN_X) {
            for (int slot = 0; slot < SLOTS; slot++) {
                double low = slot == SLOTS - 1 ? RIM : slotMinY(slot);
                double high = slot == 0 ? 16.0 - RIM : slotMaxY(slot);
                if (v >= low && v < high) return slot;
            }
        } else if (u >= BUTTON_MIN_X && u <= BUTTON_MAX_X) {
            for (int slot = 0; slot < SLOTS; slot++) {
                if (Math.abs(v - slotCenterY(slot)) <= BUTTON_HALF_HEIGHT) return slot;
            }
        }
        return -1;
    }

    private static boolean unit(double value) { return value >= 0.0 && value <= 1.0; }
}
