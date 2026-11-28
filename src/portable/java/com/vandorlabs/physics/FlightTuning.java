package com.vandorlabs.physics;

/**
 * Every tuning value behind Taxi, Hover, Flight and the Maxwell Drive, in one place.
 *
 * <p><b>Keep the player calculator in sync.</b> {@code docs/flight-calculator.html} copies these
 * values into its {@code T} table and repeats the formulas that use them ({@link FlightEnvelope},
 * {@link AircraftAttitude}, the Maxwell burn and the Hover and Taxi rules). When you change a value
 * here, or a formula that reads one, update that page in the same commit and check its worked
 * examples against the Maxwell Drive guide. Block weight estimates live in {@code VehicleWeight};
 * the calculator's material list mirrors its table.
 *
 * <p>Speeds are blocks per tick (multiply by 72 for kb/h). Weights are stone-block units.
 * NeoForge keeps its own Taxi and Hover speed table in {@code NativeGroundVehicle}.
 */
public final class FlightTuning {
    private FlightTuning() {}

    // Altitude bands.
    public static final int WORLD_CEILING = 256;
    public static final int CRUISE_ALTITUDE = 320;
    public static final int MAX_HANDOFF_ALTITUDE = 512;
    public static final int DESCENT_BRAKING_ALTITUDE = 1536;
    public static final int MAX_ALTITUDE = 10000;
    public static final int REENTRY_ALTITUDE = 200;

    // Flight speeds.
    public static final double TAKEOFF_SPEED = .32;
    public static final double LOW_SPEED = 1.5;
    public static final double MAX_CRUISE_SPEED = 15;
    /** Lowest powered Flight target speed below the cruise band. */
    public static final double MIN_FLIGHT_SPEED = .95;
    /** Share of the cruise speed allowance unlocked early in the climb above the world ceiling. */
    public static final double EARLY_CRUISE_SHARE = .55;
    public static final double EARLY_CLIMB_HEIGHT = 320;
    /** Largest speed reduction per tick while descending through the braking band. */
    public static final double DESCENT_SPEED_STEP = .15;

    // Thrust, weight and altitude envelope.
    /** Effective weight units one Maxwell thrust unit lifts at takeoff. */
    public static final double MASS_PER_THRUST = 1024;
    public static final double THRUST_ALTITUDE_LIMIT = 100000;
    public static final double ALTITUDE_THRUST_FACTOR = 24;
    /** Effective weight removed per Flight lift unit, before diminishing returns. */
    public static final double LIFT_REDUCTION_PER_UNIT = 24;
    public static final int PLATE_FLIGHT_LIFT_UNITS = 1;
    public static final int REPULSOR_FLIGHT_LIFT_UNITS = 2;
    /** Lift can never reduce effective weight below this share of the actual weight. */
    public static final double MIN_EFFECTIVE_SHARE = .25;

    // Flight acceleration and response.
    public static final double BASE_ACCELERATION = .012;
    public static final double MIN_RESPONSE = 1;
    public static final double MAX_RESPONSE = 4;
    /** Gliding target speed is BASE + min(MAX_GAIN, nose-down degrees * GAIN_PER_DEGREE). */
    public static final double GLIDE_SPEED = .78, GLIDE_DIVE_GAIN_PER_DEGREE = .026, GLIDE_DIVE_MAX_GAIN = .68;
    public static final double GLIDE_ACCELERATION = .009;
    public static final double FLIGHT_DECELERATION = .019;
    public static final double FLIGHT_DECELERATION_PER_EXCESS = .024;
    public static final double MAX_FLIGHT_DECELERATION = .12;
    /** Vertical speed during the first ticks after the nose lifts. */
    public static final double LAUNCH_CLIMB_SPEED = .17;
    /** Climb above which commanded vertical speed bleeds off: min(MAX, max(MIN, vertical * PER_SPEED)). */
    public static final double CLIMB_BAND_MIN = 12, CLIMB_BAND_MAX = 192, CLIMB_BAND_PER_SPEED = 80;

    // Attitude and handling. The response factor scales these between MIN_RESPONSE and MAX_RESPONSE.
    public static final float TRIM_PITCH = 45, MAX_PITCH = 75, MIN_PITCH = -85;
    /** Share of the pitch above TRIM_PITCH that settles back each tick. */
    public static final float PITCH_TRIM_DECAY = .085F;
    /** Thrust lost at MAX_PITCH, scaled from zero at TRIM_PITCH. */
    public static final double STEEP_CLIMB_THRUST_LOSS = .65;
    public static final double PITCH_RATE = .7, PITCH_RATE_PER_RESPONSE = .36;
    public static final float ROLL_RATE = 8.5F;
    public static final double ROLL_SHARE = .72, ROLL_SHARE_PER_RESPONSE = .07;
    public static final double TURN_RATE = .95, TURN_RATE_PER_RESPONSE = .3;
    public static final double BANK_SINK = .065, BANK_SINK_PER_SPEED = .016, MAX_BANK_SINK = .28;

    // Taxi.
    public static final double TAXI_SPEED = .4, UNPOWERED_TAXI_SPEED = .3;
    public static final double DRIVE_ACCELERATION = .018, BOOST_ACCELERATION = .028;
    public static final double COAST_DECELERATION = .008, BRAKE_DECELERATION = .045;
    /** Shedding Flight speed above the Taxi limit after touchdown, with and without the brake. */
    public static final double ROLLOUT_BRAKE_DECELERATION = .055, ROLLOUT_DECELERATION = .025;

    // Hover.
    public static final int PLATE_HOVER_LIFT = 36, REPULSOR_HOVER_LIFT = 72, VERTICAL_THRUSTER_HOVER_LIFT = 36;
    public static final int MIN_LIFT_BLOCKS = 3;
    public static final double HOVER_SPEED = .8;
    public static final double HOVER_SPRINT_MULTIPLIER = 1.25;
    public static final double HOVER_VERTICAL_SPEED = .42;
    /** Vertical acceleration is MIN + RANGE * (1 - gross weight / lift capacity). */
    public static final double HOVER_VERTICAL_ACCELERATION_MIN = .030, HOVER_VERTICAL_ACCELERATION_RANGE = .105;

    // Maxwell Drive.
    /** Thrust units are frame volume divided by this; a 3 x 3 x 4 drive has 1. */
    public static final double MAXWELL_VOLUME_PER_THRUST = 36.0;
    /** 3.0 rules, still used by NeoForge 1.21.1: one 1,000 mB tank per drive, 160 weight per thrust unit. */
    public static final int MAXWELL_CAPACITY = 1000;
    /** 3.0 rules, still used by NeoForge 1.21.1: full throttle (15) burns 1 mB every 200 ticks per drive. */
    public static final int MAXWELL_BURN_UNITS_PER_MB = 200 * 15;
    public static final double MAXWELL_DRY_MASS_PER_THRUST = 160;

    // ---- 3.1 Forge rules ----

    // Takeoff run: from RUNWAY_MIN blocks with no load to RUNWAY_MAX at the takeoff limit, scaled by load^EXPONENT.
    public static final double RUNWAY_TAKEOFF_SPEED = .6;
    public static final double RUNWAY_MIN = 30, RUNWAY_MAX = 200, RUNWAY_LOAD_EXPONENT = 2;

    /** Powered Flight speed below the cruise band, from MIN at the takeoff limit to MAX with twice the thrust needed. */
    public static final double LOW_SPEED_MIN = 1.0, LOW_SPEED_MAX = 2.0;

    /** Drive widths for the per-size tables below. */
    public static final int[] MAXWELL_WIDTHS = {3, 4, 5, 6, 9};
    /** Dry weight per thrust unit: larger drives are lighter for their thrust. */
    public static final double[] MAXWELL_SIZED_DRY_MASS_PER_THRUST = {160, 150, 140, 130, 120};
    /** Fuel burn per thrust unit relative to a 3 x 3 x 4: larger drives burn more for their thrust. */
    public static final double[] MAXWELL_SIZED_BURN_PER_THRUST = {1, 1.08, 1.16, 1.24, 1.4};
    /** Drive tank per thrust unit. Drive tanks are buffers; range comes from onboard reserve tanks. */
    public static final int MAXWELL_TANK_PER_THRUST = 250;
    /** mB per minute per thrust unit at full throttle and at the takeoff limit. */
    public static final double MAXWELL_BURN_PER_MINUTE = 100;
    /** Share of the burn that follows effective weight / takeoff limit; the rest follows thrust alone. */
    public static final double MAXWELL_LOAD_BURN_SHARE = .85;
    /** Burn accounting resolution: one millibucket is this many burn units. */
    public static final int MAXWELL_BURN_MICRO_PER_MB = 1_000_000;

    /** Gross weight one vehicle thruster moves at full Hover and Taxi speed. */
    public static final double THRUSTER_PUSH = 200;
    /** Hover speed never falls below this share of full speed while a thruster is fitted. */
    public static final double MIN_THRUSTER_SPEED_SHARE = .35;
}
