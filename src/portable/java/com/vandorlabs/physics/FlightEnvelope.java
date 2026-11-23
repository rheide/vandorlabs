package com.vandorlabs.physics;

/** Loader-independent, arcade flight envelope. Mass is measured in stone-block units. */
public final class FlightEnvelope {
    public static final int WORLD_CEILING = 256;
    public static final int CRUISE_ALTITUDE = 512;
    public static final int MAX_ALTITUDE = 10000;
    public static final int REENTRY_ALTITUDE = 200;
    public static final double TAKEOFF_SPEED = .32;
    public static final double LOW_SPEED = 1.5;
    public static final double MAX_CRUISE_SPEED = 15;
    private static final double EARLY_CRUISE_SHARE = .55;
    private static final double EARLY_CLIMB_HEIGHT = 64;
    public static final float MAX_PITCH = 45;
    private static final double MASS_PER_THRUST = 512;
    private static final double THRUST_ALTITUDE_LIMIT = 100000;
    private static final double ALTITUDE_THRUST_FACTOR = 24;

    private FlightEnvelope() {}

    public static double effectiveMass(double mass, int antigravity, int repulsors) {
        double reduction = antigravity * 24D + repulsors * 48D;
        // Additional lift remains useful on heavy craft, while the first units help small craft most.
        return mass * (.25 + .75 / (1 + reduction / Math.max(1, mass)));
    }

    public static String weightEquation(double actual, double effective) {
        long gross = Math.round(actual);
        long net = Math.round(effective);
        return "Weight: " + gross + " actual - " + (gross - net) + " hover plates = " + net;
    }

    public static double takeoffMass(double thrust) {
        return thrust * MASS_PER_THRUST;
    }

    public static boolean canFly(double mass, double thrust) {
        return mass > 0 && thrust > 0 && mass <= takeoffMass(thrust);
    }

    public static double maximumAltitude(double mass, double thrust) {
        if (!canFly(mass, thrust)) return 0;
        // Solve thrust / takeoff thrust = 1 + 24 * (Y - 256) / (100000 - Y).
        double load = mass / takeoffMass(thrust);
        double reserve = 1 - load;
        double weightedLoad = ALTITUDE_THRUST_FACTOR * load;
        return Math.min(MAX_ALTITUDE, (reserve * THRUST_ALTITUDE_LIMIT + weightedLoad * WORLD_CEILING) / (reserve + weightedLoad));
    }

    public static double cruiseSpeed(double altitude) {
        double height = Math.max(0, Math.min(1, (altitude - WORLD_CEILING) / (MAX_ALTITUDE - WORLD_CEILING)));
        return LOW_SPEED + (MAX_CRUISE_SPEED - LOW_SPEED) * height * height;
    }

    public static double cruiseSpeed(double altitude, double mass, double thrust) {
        double ceiling = maximumAltitude(mass, thrust);
        if (ceiling == 0) return 0;
        double climb = Math.max(0, Math.min(altitude, ceiling) - WORLD_CEILING);
        double cruiseHeight = MAX_ALTITUDE - WORLD_CEILING;
        double fraction = climb / cruiseHeight;
        double early = climb / (EARLY_CLIMB_HEIGHT + climb);
        double blendAtLimit = EARLY_CRUISE_SHARE * cruiseHeight / (EARLY_CLIMB_HEIGHT + cruiseHeight) + 1 - EARLY_CRUISE_SHARE;
        double blend = EARLY_CRUISE_SHARE * early + (1 - EARLY_CRUISE_SHARE) * fraction * fraction;
        double power = Math.min(1, takeoffMass(thrust) / (2 * mass));
        return LOW_SPEED + (MAX_CRUISE_SPEED - LOW_SPEED) * power * blend / blendAtLimit;
    }

    /** Opacity of the seed surface through the band where real terrain is still tracked. */
    public static double seedOpacity(double altitude) {
        double t = Math.max(0, Math.min(1, (altitude - WORLD_CEILING) / (CRUISE_ALTITUDE - WORLD_CEILING)));
        return t * t * (3 - 2 * t);
    }

    /** Shed the fraction of excess airspeed corresponding to the descent through this band. */
    public static double descentSpeed(double speed, double altitude, double drop) {
        if (drop <= 0 || speed <= LOW_SPEED || altitude - drop >= CRUISE_ALTITUDE) return speed;
        double remaining = Math.min(altitude, CRUISE_ALTITUDE) - WORLD_CEILING;
        if (remaining <= 0) return LOW_SPEED;
        double fraction = Math.max(0, altitude - drop - WORLD_CEILING) / remaining;
        return LOW_SPEED + (speed - LOW_SPEED) * fraction;
    }

    public static double acceleration(double mass, double thrust) {
        return .012 * response(mass, thrust);
    }

    public static double response(double mass, double thrust) {
        return Math.max(1, Math.min(4, takeoffMass(thrust) / Math.max(1, mass)));
    }
}
