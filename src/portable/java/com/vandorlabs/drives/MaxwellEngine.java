package com.vandorlabs.drives;

import com.vandorlabs.physics.FlightTuning;

/** Maxwell frame dimensions, inlet placement, and burn rate in signal-ticks. */
public final class MaxwellEngine {
    public static final String DESIGN = "longburn_drive";
    public static final String WIDTH_TAG = "MaxwellWidth";
    public static final int BASE_WIDTH = 3;
    // Values live in FlightTuning; keep docs/flight-calculator.html in sync with any change.
    public static final int CAPACITY = FlightTuning.MAXWELL_CAPACITY;
    public static final int BURN_UNITS_PER_MB = FlightTuning.MAXWELL_BURN_UNITS_PER_MB;

    private MaxwellEngine() {}

    public static int[] widths() { return new int[]{BASE_WIDTH, 4, 5, 6, 9}; }

    public static int depth(int width) {
        switch (width) {
            case 3: return 4;
            case 4: return 5;
            case 5: return 7;
            case 6: return 8;
            case 9: return 12;
            default: throw new IllegalArgumentException("Invalid Maxwell Drive width");
        }
    }

    public static int faceCenter(int length) { return (length - 1) / 2; }

    public static double thrustUnits(int width) { return width * width * depth(width) / FlightTuning.MAXWELL_VOLUME_PER_THRUST; }

    /** 3.0 rule, still used by NeoForge 1.21.1. Forge uses {@link #sizedDryMass(int)}. */
    public static double dryMass(int width) { return FlightTuning.MAXWELL_DRY_MASS_PER_THRUST * thrustUnits(width); }

    private static int sizeIndex(int width) {
        int[] widths = FlightTuning.MAXWELL_WIDTHS;
        for (int i = 0; i < widths.length; i++) if (widths[i] == width) return i;
        throw new IllegalArgumentException("Invalid Maxwell Drive width");
    }

    /** 3.1 Forge rule: dry weight, lighter per thrust unit for larger drives. */
    public static double sizedDryMass(int width) {
        return FlightTuning.MAXWELL_SIZED_DRY_MASS_PER_THRUST[sizeIndex(width)] * thrustUnits(width);
    }

    /** 3.1 Forge rule: drive tank capacity in mB. */
    public static int tankCapacity(int width) {
        return (int) Math.round(FlightTuning.MAXWELL_TANK_PER_THRUST * thrustUnits(width));
    }

    /** Saved fuel above a tank's capacity is kept, so 3.0 tanks of up to 1,000 mB load without loss. */
    public static int maxSavedFuel() {
        return Math.max(CAPACITY, tankCapacity(FlightTuning.MAXWELL_WIDTHS[FlightTuning.MAXWELL_WIDTHS.length - 1]));
    }

    /**
     * 3.1 Forge rule: burn this tick in millionths of a millibucket, for a throttle level of 0 to 15 and a
     * {@link com.vandorlabs.physics.FlightEnvelope#burnLoadFactor load factor} of 0 to 1.
     */
    public static long burnMicroUnits(int width, int level, double loadFactor) {
        if (level <= 0) return 0;
        double mbPerTick = thrustUnits(width) * FlightTuning.MAXWELL_SIZED_BURN_PER_THRUST[sizeIndex(width)]
                * Math.min(15, level) / 15.0 * FlightTuning.MAXWELL_BURN_PER_MINUTE / 1200.0
                * Math.max(0, Math.min(1, loadFactor));
        return Math.max(1, Math.round(mbPerTick * FlightTuning.MAXWELL_BURN_MICRO_PER_MB));
    }

    /** Converts a 3.0 partial-millibucket counter (0 to 2,999 level-ticks) to burn micro-units. */
    public static int legacyBurnToMicro(int legacyUnits) {
        long micro = Math.round((double) Math.max(0, legacyUnits) * FlightTuning.MAXWELL_BURN_MICRO_PER_MB / BURN_UNITS_PER_MB);
        return (int) Math.min(FlightTuning.MAXWELL_BURN_MICRO_PER_MB - 1, micro);
    }

    public static int throttle(boolean signalControl, boolean analog, boolean inverted, int signal, boolean flightDemand) {
        if (!signalControl) return flightDemand ? 15 : 0;
        int effective = inverted ? 15 - signal : signal;
        return analog || effective == 0 ? effective : 15;
    }

    /** 3.0 gauge for a 1,000 mB tank, still used by NeoForge 1.21.1. */
    public static int bars(int fuel, int totalBars) {
        return (fuel * totalBars + CAPACITY - 1) / CAPACITY;
    }

    /** Gauge bars for a tank of the given capacity; fuel kept above capacity shows a full gauge. */
    public static int bars(int fuel, int capacity, int totalBars) {
        if (capacity <= 0 || fuel <= 0) return 0;
        return (int) Math.min(totalBars, ((long) fuel * totalBars + capacity - 1) / capacity);
    }
}
