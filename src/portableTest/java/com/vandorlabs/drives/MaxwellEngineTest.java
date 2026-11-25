package com.vandorlabs.drives;

import com.vandorlabs.physics.FlightTuning;

/** Maxwell Drive size tables, burn accounting and 3.0 save migration. */
public final class MaxwellEngineTest {
    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int micro = FlightTuning.MAXWELL_BURN_MICRO_PER_MB;
        require(MaxwellEngine.tankCapacity(3) == 250 && MaxwellEngine.tankCapacity(6) == 2000 && MaxwellEngine.tankCapacity(9) == 6750,
                "drive tanks scale with thrust units");
        require(MaxwellEngine.maxSavedFuel() == 6750, "saved fuel bound covers the largest tank and 3.0 tanks");
        double previous = Double.MAX_VALUE;
        for (int width : MaxwellEngine.widths()) {
            double perThrust = MaxwellEngine.sizedDryMass(width) / MaxwellEngine.thrustUnits(width);
            require(perThrust < previous, "larger drives weigh less per thrust unit, width=" + width);
            previous = perThrust;
            require(MaxwellEngine.dryMass(width) == 160 * MaxwellEngine.thrustUnits(width), "3.0 dry weight is unchanged for NeoForge");
        }
        require(MaxwellEngine.sizedDryMass(3) == 160 && Math.abs(MaxwellEngine.sizedDryMass(9) - 120 * 27) < 1e-9, "size table endpoints");

        long full = MaxwellEngine.burnMicroUnits(3, 15, 1);
        require(Math.abs(full * 1200.0 / micro - FlightTuning.MAXWELL_BURN_PER_MINUTE) < .01, "a 3 x 3 x 4 at the takeoff limit burns the per-minute rate");
        require(MaxwellEngine.burnMicroUnits(3, 0, 1) == 0 && MaxwellEngine.burnMicroUnits(3, 15, 0) == 1, "no throttle burns nothing; any throttle burns something");
        require(Math.abs(MaxwellEngine.burnMicroUnits(3, 7, 1) - full * 7 / 15.0) <= 1, "analog throttle scales burn");
        require(Math.abs(MaxwellEngine.burnMicroUnits(3, 15, .5) - full / 2.0) <= 1, "load factor scales burn");
        double perThrustSmall = MaxwellEngine.burnMicroUnits(3, 15, 1) / MaxwellEngine.thrustUnits(3);
        double perThrustLarge = MaxwellEngine.burnMicroUnits(9, 15, 1) / MaxwellEngine.thrustUnits(9);
        require(Math.abs(perThrustLarge / perThrustSmall - 1.4) < 1e-4, "larger drives burn more per thrust unit");
        require(MaxwellEngine.burnMicroUnits(9, 15, 1) > micro, "a large drive can burn more than 1 mB per tick");

        require(MaxwellEngine.legacyBurnToMicro(0) == 0 && MaxwellEngine.legacyBurnToMicro(1500) == micro / 2
                && MaxwellEngine.legacyBurnToMicro(2999) == micro - 333 && MaxwellEngine.legacyBurnToMicro(9999) == micro - 1
                && MaxwellEngine.legacyBurnToMicro(-5) == 0, "3.0 partial burn converts and stays below one millibucket");

        require(MaxwellEngine.bars(250, 250, 8) == 8 && MaxwellEngine.bars(1000, 250, 8) == 8 && MaxwellEngine.bars(1, 250, 8) == 1
                && MaxwellEngine.bars(0, 250, 8) == 0 && MaxwellEngine.bars(5, 0, 8) == 0, "sized gauge clamps over-full 3.0 tanks");
        require(MaxwellEngine.bars(500, 8) == 4, "3.0 gauge is unchanged for NeoForge");
        System.out.println("Maxwell engine PASS sized tanks/weights/burn, analog and load scaling, 3.0 migration, gauge");
    }

    private MaxwellEngineTest() {}
}
