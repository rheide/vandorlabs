package com.vandorlabs.machines;

/** Shared recipe cost shown by browsers and consumed by both platform runtimes. */
public final class SynthesisRecipePolicy {
    public static final int REQUIRED_RF = 8000;
    public static final int MINIMUM_TICKS = 400;
    public static final int RF_PER_TICK = REQUIRED_RF / MINIMUM_TICKS;
    private SynthesisRecipePolicy() {}
}
