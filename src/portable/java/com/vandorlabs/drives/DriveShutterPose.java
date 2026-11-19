package com.vandorlabs.drives;

/** Constant-speed reversible shutter travel with smooth endpoints on a pause-aware visual clock. */
public final class DriveShutterPose {
    private static final double DURATION = 20;
    private boolean initialized, open;
    private double previous, travel;

    public double sample(boolean targetOpen, double time) {
        if (!initialized) {
            initialized = true;
            open = targetOpen;
            travel = open ? 1 : 0;
            previous = time;
        }
        double step = Math.max(0, time - previous) / DURATION;
        travel = open ? Math.min(1, travel + step) : Math.max(0, travel - step);
        previous = Math.max(previous, time);
        open = targetOpen;
        return travel;
    }

    public static double easedTravel(double travel) {
        return .5125 * travel * travel * (3 - 2 * travel);
    }
}
