package com.vandorlabs.appliance;

/** Redstone activates once when the selected signal edge arrives. */
public final class ApplianceAutomation {
    public static final int MANUAL = 0;
    public static final int ON = 1;
    public static final int OFF = 2;

    public static boolean valid(int mode) {
        return mode >= MANUAL && mode <= OFF;
    }

    public static boolean triggers(int mode, boolean previous, boolean powered) {
        return previous != powered && mode == (powered ? ON : OFF);
    }

    public static String label(int mode) {
        return mode == ON ? "Redstone On" : mode == OFF ? "Redstone Off" : "Manual";
    }

    private ApplianceAutomation() {}
}
