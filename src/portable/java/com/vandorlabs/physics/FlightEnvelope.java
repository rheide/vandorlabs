package com.vandorlabs.physics;

/** Loader-independent, arcade flight envelope. Mass is measured in stone-block units. */
public final class FlightEnvelope {
    public static final int WORLD_CEILING = 256;
    public static final int MAX_ALTITUDE = 10000;
    public static final int REENTRY_ALTITUDE = 200;
    public static final double TAKEOFF_SPEED = .32;
    public static final double LOW_SPEED = 1.5;
    public static final float MAX_PITCH = 45;
    private static final double MASS_PER_THRUST = 512;
    private static final double THRUST_ALTITUDE_LIMIT = 100000;

    private FlightEnvelope() {}

    public static double effectiveMass(double mass, int antigravity, int repulsors) {
        double reduction = antigravity * 24D + repulsors * 48D;
        // Additional lift remains useful on heavy craft, while the first units help small craft most.
        return mass * (.25 + .75 / (1 + reduction / Math.max(1, mass)));
    }

    public static double takeoffMass(double thrust) {
        return thrust * MASS_PER_THRUST;
    }

    public static boolean canFly(double mass, double thrust) {
        return mass > 0 && thrust > 0 && mass <= takeoffMass(thrust);
    }

    public static double maximumAltitude(double mass, double thrust) {
        if (!canFly(mass, thrust)) return 0;
        double reserve = 1 - mass / takeoffMass(thrust);
        return Math.min(MAX_ALTITUDE, WORLD_CEILING + (THRUST_ALTITUDE_LIMIT - WORLD_CEILING) * reserve * reserve);
    }

    public static double cruiseSpeed(double altitude) {
        double height = Math.max(0, Math.min(1, (altitude - WORLD_CEILING) / (MAX_ALTITUDE - WORLD_CEILING)));
        return LOW_SPEED * (1 + 49 * height * height);
    }

    public static double acceleration(double mass, double thrust) {
        return .012 * response(mass, thrust);
    }

    public static double response(double mass, double thrust) {
        return Math.max(1, Math.min(4, takeoffMass(thrust) / Math.max(1, mass)));
    }
}
