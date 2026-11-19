package com.vandorlabs.machines;

/** Scanner and gantry motion are driven by the pause-aware client clock while a job spends RF. */
public final class BuilderFabricationPose {
    private double sampled, phase;
    private boolean initialized, working;

    public void observe(double time, boolean working) {
        if (initialized && this.working) phase += Math.max(0, time - sampled);
        sampled = time;
        this.working = working;
        initialized = true;
    }

    public double phase() { return phase; }
    public double headX() { return headX(phase); }
    public double armZ() { return armZ(phase); }
    public static double headX(double phase) { return 1.25 * Math.sin(phase * .07); }
    public static double armZ(double phase) { return .75 * Math.sin(phase * .043); }
}
