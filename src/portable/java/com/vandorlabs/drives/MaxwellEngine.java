package com.vandorlabs.drives;

/** Maxwell frame dimensions, inlet placement, and burn rate in signal-ticks. */
public final class MaxwellEngine {
    public static final String DESIGN = "longburn_drive";
    public static final String WIDTH_TAG = "MaxwellWidth";
    public static final int CAPACITY = 1000;
    public static final int BURN_UNITS_PER_MB = 200 * 15;

    private MaxwellEngine() {}

    public static int[] widths() { return new int[]{3, 4, 5, 6, 9}; }

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

    public static double thrustUnits(int width) { return width * width * depth(width) / 36.0; }

    public static double dryMass(int width) { return 160 * thrustUnits(width); }

    public static int throttle(boolean signalControl, boolean analog, boolean inverted, int signal, boolean flightDemand) {
        if (!signalControl) return flightDemand ? 15 : 0;
        int effective = inverted ? 15 - signal : signal;
        return analog || effective == 0 ? effective : 15;
    }

    public static int bars(int fuel, int totalBars) {
        return (fuel * totalBars + CAPACITY - 1) / CAPACITY;
    }
}
