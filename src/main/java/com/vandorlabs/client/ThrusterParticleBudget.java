package com.vandorlabs.client;

import java.lang.ref.WeakReference;

/** Admission happens before particle construction and never changes an emitter's game state. */
public final class ThrusterParticleBudget {
    static final double MAX_DISTANCE_SQUARED = 64D * 64D;
    static final int ALL_LIMIT = 256, DECREASED_LIMIT = 64;
    private WeakReference<Object> world = new WeakReference<>(null);
    private long tick;
    private int admitted, attempts;

    public boolean allow(Object currentWorld, long currentTick, int setting, double distanceSquared) {
        if (currentWorld == null || setting >= 2 || !Double.isFinite(distanceSquared)
                || distanceSquared < 0 || distanceSquared > MAX_DISTANCE_SQUARED) return false;
        if (world.get() != currentWorld) {
            world = new WeakReference<>(currentWorld);
            tick = currentTick;
            admitted = attempts = 0;
        } else if (tick != currentTick) {
            tick = currentTick;
            admitted = attempts = 0;
        }
        // Alternate the phase each tick so a single small emitter is not permanently skipped.
        if (setting == 1 && ((attempts++ + currentTick) & 1L) != 0) return false;
        if (admitted >= (setting == 1 ? DECREASED_LIMIT : ALL_LIMIT)) return false;
        admitted++;
        return true;
    }
}
