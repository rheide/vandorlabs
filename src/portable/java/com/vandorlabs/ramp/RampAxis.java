package com.vandorlabs.ramp;

/** Mount-dependent axis choices. Kept independent of Minecraft metadata. */
public enum RampAxis {
    X, Y, Z;

    /** Side mounts offer both horizontal axes and Y; top/bottom mounts offer X/Z. */
    public static RampAxis[] choices(RampAxis mountingAxis) {
        if (mountingAxis == X) return new RampAxis[]{X, Z, Y};
        if (mountingAxis == Z) return new RampAxis[]{Z, X, Y};
        return new RampAxis[]{X, Z};
    }

    public static boolean allowed(RampAxis mountingAxis, RampAxis selected) {
        for (RampAxis axis : choices(mountingAxis)) if (axis == selected) return true;
        return false;
    }

    public RampAxis rotateQuarterTurn() {
        return this == X ? Z : this == Z ? X : Y;
    }
}
