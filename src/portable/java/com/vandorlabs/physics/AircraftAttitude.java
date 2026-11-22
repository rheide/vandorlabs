package com.vandorlabs.physics;

/** Bank and high-angle pitch response for the Forge aircraft controls. */
public final class AircraftAttitude {
    public static final float TRIM_PITCH = 45, MAX_PITCH = 75, MAX_BANK = 32;
    private AircraftAttitude() {}

    public static float pitch(float current, float input) {
        float next = current + input;
        if (next > TRIM_PITCH) next -= (next - TRIM_PITCH) * .085F;
        return Math.max(-TRIM_PITCH, Math.min(MAX_PITCH, next));
    }

    public static float bank(float current, int steer) {
        float target = MAX_BANK * steer;
        return current + Math.max(-2.8F, Math.min(2.8F, target - current));
    }

    public static double propulsion(float pitch) {
        return 1 - .65 * Math.min(1, Math.max(0, (pitch - TRIM_PITCH) / (MAX_PITCH - TRIM_PITCH)));
    }

    public static boolean validPitch(float pitch) {
        return Float.isFinite(pitch) && pitch >= -TRIM_PITCH && pitch <= MAX_PITCH;
    }

    public static boolean validBank(float bank) {
        return Float.isFinite(bank) && Math.abs(bank) <= MAX_BANK;
    }
}
