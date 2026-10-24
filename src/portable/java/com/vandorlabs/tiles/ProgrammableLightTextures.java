package com.vandorlabs.tiles;

/** The five artwork choices shared by the light menu and renderer. */
public final class ProgrammableLightTextures {
    public static final String[] IDS = {
            "porthole", "light_column_wall", "slatted_lamp", "window_lamp", "lightbar_wall", "logo"
    };

    private static final String[][] TEXTURES = new String[IDS.length][2];
    static {
        for (int i=0; i<IDS.length; i++) {
            TEXTURES[i][0] = "vandorlabs:blocks/" + IDS[i] + "_off";
            TEXTURES[i][1] = "vandorlabs:blocks/" + IDS[i] + "_on";
        }
    }
    private ProgrammableLightTextures() { }

    public static int clamp(int choice) {
        return choice >= 0 && choice < IDS.length ? choice : 0;
    }

    public static String texture(int choice, boolean lit) {
        return TEXTURES[clamp(choice)][lit ? 1 : 0];
    }
}
