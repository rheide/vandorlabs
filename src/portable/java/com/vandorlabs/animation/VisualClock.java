package com.vandorlabs.animation;

/** Client visual ticks independent of server time corrections. */
public final class VisualClock {
    private long previous;
    private boolean initialized, wasPaused;
    private double ticks;
    public double sample(long nanos, boolean paused) {
        if (initialized && !paused && !wasPaused)
            ticks += Math.max(0, nanos - previous) / 50000000.0;
        initialized=true; previous=nanos; wasPaused=paused;
        return ticks;
    }
}
