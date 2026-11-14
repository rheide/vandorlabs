package com.vandorlabs.rack;

/** Cosmetic insertion envelope, independent of server/world time and rendering. */
public final class ModuleRackMotion {
    public static final double DURATION_TICKS = 10.0;
    public static final double TRAVEL_BLOCKS = 8.0 / 16.0;

    private ModuleRackMotion() { }

    public static double offset(double ageTicks) {
        if (!(ageTicks > 0)) return TRAVEL_BLOCKS;
        if (ageTicks >= DURATION_TICKS) return 0;
        double remaining = 1 - ageTicks / DURATION_TICKS;
        // Smoothstep starts and finishes at rest, with no overshoot into the backplane.
        return TRAVEL_BLOCKS * remaining * remaining * (3 - 2 * remaining);
    }

    /** Initial chunk data is already seated. A repeated packet is not a new insertion. */
    public static boolean starts(boolean initial, int previousMask, int mask, int slot,
                                 long previousSequence, long sequence) {
        int bit = 1 << slot;
        return !initial && ModuleRackGeometry.validSlot(slot) && (mask & bit) != 0
                && ((previousMask & bit) == 0 || sequence != previousSequence);
    }
}
