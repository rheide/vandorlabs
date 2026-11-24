package com.vandorlabs.screen;

/** Stable saved choices for the Vehicle Status folder. */
public enum VehicleInstrument {
    STATUS(-3, "Status"),
    ATTITUDE(-21, "Attitude Indicator"),
    COMPASS(-22, "Compass"),
    ALTIMETER(-23, "Altimeter"),
    AIRSPEED(-24, "Airspeed Indicator"),
    HUD(-25, "HUD (Small)"),
    HUD_LARGE(-26, "HUD (Large)"),
    AUTOPILOT(-28, "Autopilot");

    public boolean isHud() { return this == HUD || this == HUD_LARGE; }


    private static final VehicleInstrument[] CHOICES = values();
    public final int choice;
    public final String label;

    VehicleInstrument(int choice, String label) {
        this.choice = choice;
        this.label = label;
    }

    public static VehicleInstrument forChoice(int choice) {
        if (choice >= 0) return null;
        for (VehicleInstrument instrument : CHOICES) {
            if (instrument.choice == choice) return instrument;
        }
        return null;
    }

    public static boolean interactive(int choice){return choice==STATUS.choice||choice==AUTOPILOT.choice;}

    public static boolean includes(int choice) {
        return forChoice(choice) != null;
    }
}
