package com.vandorlabs.tiles;

/** The five artwork choices shared by the light menu and renderer. */
public final class ProgrammableLightTextures {
    public static final String[] IDS = {
            "porthole", "light_column_wall", "slatted_lamp", "window_lamp", "lightbar_wall", "logo"
    };

    private ProgrammableLightTextures() { }

    public static int clamp(int choice) {
        return choice >= 0 && choice < IDS.length ? choice : 0;
    }

    public static String texture(int choice, boolean lit) {
        return "vandorlabs:blocks/" + IDS[clamp(choice)] + (lit ? "_on" : "_off");
    }
}
