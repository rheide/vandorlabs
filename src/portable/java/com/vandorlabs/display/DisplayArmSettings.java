package com.vandorlabs.display;

/** Saved, renderer-independent display-arm settings. Distances are model pixels. */
public final class DisplayArmSettings {
    public static final int MIN_OFFSET = -32, MAX_OFFSET = 32;
    /** Four pixels of forward clearance keep a fresh floor/ceiling arm behind its screen. */
    public static final DisplayArmSettings DEFAULT = new DisplayArmSettings(
            false, false, 0, 0, 4, 0, 0, 0);

    public final boolean half, input;
    public final int right, height, forward, yaw, pitch, roll;

    /** Clamp persisted data; network adapters must call valid before accepting an edit. */
    public DisplayArmSettings(boolean half, boolean input, int right, int height,
            int forward, int yaw, int pitch, int roll) {
        this.half = half;
        this.input = input;
        this.right = clamp(right, MIN_OFFSET, MAX_OFFSET);
        this.height = clamp(height, MIN_OFFSET, MAX_OFFSET);
        this.forward = clamp(forward, MIN_OFFSET, MAX_OFFSET);
        this.yaw = clamp(yaw, -180, 180);
        this.pitch = clamp(pitch, -90, 90);
        this.roll = clamp(roll, -180, 180);
    }

    public static boolean valid(boolean half, boolean input, int right, int height,
            int forward, int yaw, int pitch, int roll) {
        return between(right, MIN_OFFSET, MAX_OFFSET)
                && between(height, MIN_OFFSET, MAX_OFFSET)
                && between(forward, MIN_OFFSET, MAX_OFFSET)
                && between(yaw, -180, 180) && between(pitch, -90, 90)
                && between(roll, -180, 180);
    }

    private static boolean between(int value, int low, int high) {
        return value >= low && value <= high;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DisplayArmSettings)) return false;
        DisplayArmSettings value = (DisplayArmSettings) other;
        return half == value.half && input == value.input && right == value.right
                && height == value.height && forward == value.forward
                && yaw == value.yaw && pitch == value.pitch && roll == value.roll;
    }

    @Override public int hashCode() {
        int hash = (half ? 1 : 0) * 31 + (input ? 1 : 0);
        for (int value : new int[]{right, height, forward, yaw, pitch, roll})
            hash = 31 * hash + value;
        return hash;
    }
}
