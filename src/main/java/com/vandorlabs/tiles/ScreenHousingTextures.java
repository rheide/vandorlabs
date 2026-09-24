package com.vandorlabs.tiles;

/** Shared menu choices and atlas names for programmable housing surfaces. */
public final class ScreenHousingTextures {
    public static final String[] IDS = {
            "dark_wall_panel", "light_wall_panel", "light_alloy_hull",
            "metal_floor", "dark_gunmetal_hull", "midnight_matte_hull",
            "dark_industrial_panel", "light_industrial_panel", "ribbed_wall",
            "industrial_block", "industrial_trim", "industrial_grate"
    };
    private static final String[] TEXTURES = {
            "dark_wall_panel", "light_wall_panel", "light_alloy_hull",
            "metal_floor", "dark_gunmetal_hull", "midnight_matte_hull",
            "thrusters/dark_trim", "thrusters/trim", "ribbed_wall",
            "thrusters/side", "thrusters/top", "thrusters/rear"
    };

    private ScreenHousingTextures() { }

    public static int clamp(int choice) {
        return choice >= 0 && choice < IDS.length ? choice : 0;
    }

    public static String texture(int choice) {
        return "vandorlabs:blocks/" + TEXTURES[clamp(choice)];
    }
}
