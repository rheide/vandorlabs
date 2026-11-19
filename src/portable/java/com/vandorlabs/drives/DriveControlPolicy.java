package com.vandorlabs.drives;

/** Standalone Drive Redstone, brightness and exhaust controls. */
public final class DriveControlPolicy {
    public static int clampLevel(int level) {
        return Math.max(0, Math.min(15, level));
    }

    public static int activation(boolean usesSignal, boolean inverted, int raw) {
        if (!usesSignal) return 15;
        int level = clampLevel(raw);
        return inverted ? 15 - level : level;
    }

    public static boolean plume(boolean active, boolean brightnessTracksSignal, int particles, int threshold, int level) {
        return active && particles > 0 && (!brightnessTracksSignal || level >= threshold);
    }

    public static int plumeCount(int particles) {
        return 8 * (particles == 2 ? 3 : particles == 3 ? 6 : 1);
    }

    private DriveControlPolicy() {}
}
