package com.vandorlabs.machines;

/** Shared recipe cost shown by browsers and consumed by both platform runtimes. */
public final class SynthesisRecipePolicy {
    public static final int REQUIRED_RF = 8000;
    public static final int MINIMUM_TICKS = 400;
    public static final int RF_PER_TICK = REQUIRED_RF / MINIMUM_TICKS;
    public static int minimumTicks(String machine){return "assembly_press".equals(machine)||"enclosed_gantry".equals(machine)?200:MINIMUM_TICKS;}
    public static int rfPerTick(String machine){return REQUIRED_RF/minimumTicks(machine);}
    private SynthesisRecipePolicy() {}
}
