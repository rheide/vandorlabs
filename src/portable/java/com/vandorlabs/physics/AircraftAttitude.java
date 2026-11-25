package com.vandorlabs.physics;

/** Control rates and lift response shared by Forge's aircraft and its tests. */
public final class AircraftAttitude {
    // Values live in FlightTuning; keep docs/flight-calculator.html in sync with any change.
    public static final float TRIM_PITCH = FlightTuning.TRIM_PITCH, MAX_PITCH = FlightTuning.MAX_PITCH, MIN_PITCH = FlightTuning.MIN_PITCH;
    private static final float ROLL_RATE = FlightTuning.ROLL_RATE;

    private AircraftAttitude() {}

    public static float pitch(float current, float input) {
        float next = current + input;
        if (next > TRIM_PITCH) next -= (next - TRIM_PITCH) * FlightTuning.PITCH_TRIM_DECAY;
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, next));
    }

    public static float rollRate(float current, int steer, double response) {
        float target = ROLL_RATE * steer * (float) (FlightTuning.ROLL_SHARE + FlightTuning.ROLL_SHARE_PER_RESPONSE * response);
        return approach(current, target, steer == 0 ? 2.25F : 1.35F);
    }

    public static float bank(float current, float rate) {
        return wrap(current + rate);
    }

    public static float levelBank(float current) {
        return approach(wrap(current), 0, 4);
    }

    public static float coordinatedTurn(double roll, double response) {
        return (float) (Math.sin(roll) * (FlightTuning.TURN_RATE + FlightTuning.TURN_RATE_PER_RESPONSE * response));
    }

    public static double bankSink(double roll, double speed) {
        return (1 - Math.cos(roll)) * Math.min(FlightTuning.MAX_BANK_SINK, FlightTuning.BANK_SINK + speed * FlightTuning.BANK_SINK_PER_SPEED);
    }

    public static float recoveryPitch(float current, float pilotPitch, double excessAltitude, boolean gliding) {
        float glideAngle = gliding ? 14 : 5;
        if (!gliding) glideAngle += (float) Math.min(11, Math.log1p(excessAltitude) * 1.35);
        return Math.min(pilotPitch, approach(current, -glideAngle, 2.25F));
    }

    public static double propulsion(float pitch) {
        return 1 - FlightTuning.STEEP_CLIMB_THRUST_LOSS * Math.min(1, Math.max(0, (pitch - TRIM_PITCH) / (MAX_PITCH - TRIM_PITCH)));
    }

    public static boolean validPitch(float pitch) {
        return Float.isFinite(pitch) && pitch >= MIN_PITCH && pitch <= MAX_PITCH;
    }

    public static boolean validBank(float bank) {
        return Float.isFinite(bank) && bank >= -180 && bank < 180;
    }

    public static float wrap(float angle) {
        angle %= 360;
        if (angle >= 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }

    private static float approach(float current, float target, float distance) {
        return current + Math.max(-distance, Math.min(distance, target - current));
    }
}
