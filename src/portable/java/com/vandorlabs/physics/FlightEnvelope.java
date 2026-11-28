package com.vandorlabs.physics;

/** Loader-independent, arcade flight envelope. Mass is measured in stone-block units. */
public final class FlightEnvelope {
    // Values live in FlightTuning; keep docs/flight-calculator.html in sync with any change.
    public static final int WORLD_CEILING = FlightTuning.WORLD_CEILING;
    public static final int CRUISE_ALTITUDE = FlightTuning.CRUISE_ALTITUDE;
    public static final int MAX_HANDOFF_ALTITUDE = FlightTuning.MAX_HANDOFF_ALTITUDE;
    public static final int DESCENT_BRAKING_ALTITUDE = FlightTuning.DESCENT_BRAKING_ALTITUDE;
    public static final int MAX_ALTITUDE = FlightTuning.MAX_ALTITUDE;
    public static final int REENTRY_ALTITUDE = FlightTuning.REENTRY_ALTITUDE;
    public static final double TAKEOFF_SPEED = FlightTuning.TAKEOFF_SPEED;
    public static final double LOW_SPEED = FlightTuning.LOW_SPEED;
    public static final double MAX_CRUISE_SPEED = FlightTuning.MAX_CRUISE_SPEED;
    private static final double EARLY_CRUISE_SHARE = FlightTuning.EARLY_CRUISE_SHARE;
    private static final double EARLY_CLIMB_HEIGHT = FlightTuning.EARLY_CLIMB_HEIGHT;
    public static final float MAX_PITCH = FlightTuning.TRIM_PITCH;
    private static final double MASS_PER_THRUST = FlightTuning.MASS_PER_THRUST;
    private static final double THRUST_ALTITUDE_LIMIT = FlightTuning.THRUST_ALTITUDE_LIMIT;
    private static final double ALTITUDE_THRUST_FACTOR = FlightTuning.ALTITUDE_THRUST_FACTOR;

    private FlightEnvelope() {}

    public static double effectiveMass(double mass, int antigravity, int repulsors) {
        double reduction = antigravity * (FlightTuning.PLATE_FLIGHT_LIFT_UNITS * FlightTuning.LIFT_REDUCTION_PER_UNIT)
                + repulsors * (FlightTuning.REPULSOR_FLIGHT_LIFT_UNITS * FlightTuning.LIFT_REDUCTION_PER_UNIT);
        // Additional lift remains useful on heavy craft, while the first units help small craft most.
        double floor = FlightTuning.MIN_EFFECTIVE_SHARE;
        return mass * (floor + (1 - floor) / (1 + reduction / Math.max(1, mass)));
    }

    public static String weightEquation(double actual, double effective) {
        long gross = Math.round(actual);
        long net = Math.round(effective);
        return "Weight: " + gross + " actual - " + (gross - net) + " hover plates = " + net;
    }

    public static double takeoffMass(double thrust) {
        return thrust * MASS_PER_THRUST;
    }

    /**
     * 3.1 Forge rule: powered speed target on a low approach. Above {@link FlightTuning#APPROACH_CEILING} blocks of
     * smoothed ground clearance, or while climbing, the target is unchanged; below it eases to the approach speed.
     */
    public static double approachSpeed(double target, double clearance, boolean climbing) {
        if (climbing || !(clearance < FlightTuning.APPROACH_CEILING) || target <= FlightTuning.APPROACH_SPEED) return target;
        double t = Math.max(0, Math.min(1, (clearance - FlightTuning.APPROACH_FLOOR) / (FlightTuning.APPROACH_CEILING - FlightTuning.APPROACH_FLOOR)));
        t = t * t * (3 - 2 * t);
        return FlightTuning.APPROACH_SPEED + (target - FlightTuning.APPROACH_SPEED) * t;
    }

    /** Moving average of ground clearance for {@link #approachSpeed}; an unknown clearance reads as high. */
    public static double smoothClearance(double average, double clearance) {
        double sample = Double.isFinite(clearance) ? Math.min(clearance, 2 * FlightTuning.APPROACH_CEILING) : 2 * FlightTuning.APPROACH_CEILING;
        if (!Double.isFinite(average)) return sample;
        return average + (sample - average) * FlightTuning.APPROACH_SMOOTHING;
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

    /** 3.0 rule, still used by NeoForge 1.21.1: powered speed starts from LOW_SPEED at the world ceiling. */
    public static double cruiseSpeed(double altitude, double mass, double thrust) {
        return cruiseSpeedFrom(LOW_SPEED, altitude, mass, thrust);
    }

    /** 3.1 Forge rule: like {@link #cruiseSpeed(double, double, double)}, starting from {@link #lowAltitudeSpeed}. */
    public static double reserveCruiseSpeed(double altitude, double mass, double thrust) {
        if (maximumAltitude(mass, thrust) == 0) return 0;
        return cruiseSpeedFrom(lowAltitudeSpeed(mass, thrust), altitude, mass, thrust);
    }

    /** Powered speed below the cruise band: LOW_SPEED_MIN at the takeoff limit up to LOW_SPEED_MAX with twice the thrust needed. */
    public static double lowAltitudeSpeed(double mass, double thrust) {
        double reserve = Math.max(0, Math.min(1, (thrustReserve(mass, thrust) - .5) / .5));
        return FlightTuning.LOW_SPEED_MIN + (FlightTuning.LOW_SPEED_MAX - FlightTuning.LOW_SPEED_MIN) * reserve;
    }

    private static double thrustReserve(double mass, double thrust) {
        return Math.min(1, takeoffMass(thrust) / (2 * mass));
    }

    /** Effective weight as a share of the current takeoff limit, from 0 to 1; 1 without thrust. */
    public static double takeoffLoad(double mass, double thrust) {
        return thrust > 0 ? Math.max(0, Math.min(1, mass / takeoffMass(thrust))) : 1;
    }

    /** 3.1 Forge rule: blocks of runway to reach {@link FlightTuning#RUNWAY_TAKEOFF_SPEED}. */
    public static double runwayLength(double mass, double thrust) {
        return FlightTuning.RUNWAY_MIN + (FlightTuning.RUNWAY_MAX - FlightTuning.RUNWAY_MIN)
                * Math.pow(takeoffLoad(mass, thrust), FlightTuning.RUNWAY_LOAD_EXPONENT);
    }

    /** 3.1 Forge rule: constant takeoff-roll acceleration that covers {@link #runwayLength} before takeoff speed. */
    public static double takeoffAcceleration(double mass, double thrust) {
        double speed = FlightTuning.RUNWAY_TAKEOFF_SPEED;
        return speed * speed / (2 * runwayLength(mass, thrust));
    }

    /** 3.1 Forge rule: Maxwell burn multiplier. A lightly loaded craft burns less; a craft at its takeoff limit burns 1. */
    public static double burnLoadFactor(double mass, double thrust) {
        double share = FlightTuning.MAXWELL_LOAD_BURN_SHARE;
        return 1 - share + share * takeoffLoad(mass, thrust);
    }

    private static double cruiseSpeedFrom(double lowSpeed, double altitude, double mass, double thrust) {
        double ceiling = maximumAltitude(mass, thrust);
        if (ceiling == 0) return 0;
        double climb = Math.max(0, Math.min(altitude, ceiling) - WORLD_CEILING);
        double cruiseHeight = MAX_ALTITUDE - WORLD_CEILING;
        double fraction = climb / cruiseHeight;
        double early = climb * climb / (EARLY_CLIMB_HEIGHT * EARLY_CLIMB_HEIGHT + climb * climb);
        double earlyAtLimit = cruiseHeight * cruiseHeight / (EARLY_CLIMB_HEIGHT * EARLY_CLIMB_HEIGHT + cruiseHeight * cruiseHeight);
        double blendAtLimit = EARLY_CRUISE_SHARE * earlyAtLimit + (1 - EARLY_CRUISE_SHARE);
        double blend = EARLY_CRUISE_SHARE * early + (1 - EARLY_CRUISE_SHARE) * fraction * fraction;
        double power = thrustReserve(mass, thrust);
        return lowSpeed + (MAX_CRUISE_SPEED - lowSpeed) * power * (blend / blendAtLimit);
    }

    /** Opacity of the seed surface through the band where real terrain is still tracked. */
    public static double seedOpacity(double altitude) {
        return seedOpacity(altitude,CRUISE_ALTITUDE);
    }

    public static double seedOpacity(double altitude, int handoffAltitude) {
        if(handoffAltitude<=WORLD_CEILING)return altitude>WORLD_CEILING?1:0;
        double t = Math.max(0, Math.min(1, (altitude - WORLD_CEILING) / (handoffAltitude - WORLD_CEILING)));
        return t * t * (3 - 2 * t);
    }

    /** Bleed off commanded climb over roughly four seconds of remaining headroom. */
    public static double sustainableClimb(double vertical,double remaining) {
        if(vertical<=0)return vertical;
        double room=Math.max(0,remaining);
        double band=Math.min(FlightTuning.CLIMB_BAND_MAX,Math.max(FlightTuning.CLIMB_BAND_MIN,vertical*FlightTuning.CLIMB_BAND_PER_SPEED));
        return Math.min(room,vertical*Math.min(1,room/band));
    }

    /** Shed the fraction of excess airspeed corresponding to the descent through this band. */
    public static double descentSpeed(double speed, double altitude, double drop) {
        if (drop <= 0 || speed <= LOW_SPEED || altitude - drop >= DESCENT_BRAKING_ALTITUDE) return speed;
        double remaining = Math.min(altitude, DESCENT_BRAKING_ALTITUDE) - WORLD_CEILING;
        double fraction = remaining <= 0 ? 0 : Math.max(0, altitude - drop - WORLD_CEILING) / remaining;
        // Give up most excess speed high in the approach. Bound the impulse for an
        // aircraft restored from an older save already diving below the braking band.
        double desired = LOW_SPEED + (speed - LOW_SPEED) * fraction * fraction;
        return Math.max(speed - FlightTuning.DESCENT_SPEED_STEP, desired);
    }

    public static double acceleration(double mass, double thrust) {
        return FlightTuning.BASE_ACCELERATION * response(mass, thrust);
    }

    public static double response(double mass, double thrust) {
        return Math.max(FlightTuning.MIN_RESPONSE, Math.min(FlightTuning.MAX_RESPONSE, takeoffMass(thrust) / Math.max(1, mass)));
    }
}
