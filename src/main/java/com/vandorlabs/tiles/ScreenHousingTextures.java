package com.vandorlabs.tiles;

/** Shared menu choices and atlas names for programmable housing surfaces. */
public final class ScreenHousingTextures {
    private static final class Finish {
        final String id;
        final String texture;
        Finish(String id, String texture) { this.id = id; this.texture = texture; }
    }

    /** One ordered list keeps saved indices, menu names and atlas paths aligned. */
    private static final Finish[] FINISHES = {
            new Finish("dark_wall_panel", "dark_wall_panel"),
            new Finish("light_wall_panel", "light_wall_panel"),
            new Finish("light_alloy_hull", "light_alloy_hull"),
            new Finish("metal_floor", "metal_floor"),
            new Finish("dark_gunmetal_hull", "dark_gunmetal_hull"),
            new Finish("midnight_matte_hull", "midnight_matte_hull"),
            new Finish("dark_industrial_panel", "thrusters/dark_trim"),
            new Finish("light_industrial_panel", "thrusters/trim"),
            new Finish("ribbed_wall", "ribbed_wall"),
            new Finish("industrial_block", "thrusters/side"),
            new Finish("industrial_trim", "thrusters/top"),
            new Finish("industrial_grate", "thrusters/rear"),
            new Finish("wall_vent", "wall_vent"),
            new Finish("bolted_wall_plate", "bolted_wall_plate"),
            new Finish("burgundy", "burgundy_carpet"),
            new Finish("bluegray", "bluegray_carpet"),
            new Finish("matter", "matter"),
            new Finish("matter_amber", "matter_amber"),
            new Finish("matter_cyan", "matter_cyan"),
            new Finish("matter_red", "matter_red"),
            new Finish("wall_pipes", "wall_pipes"),
            new Finish("framed_wall_pipes", "framed_wall_pipes"),
            new Finish("midnight_satin_hull", "midnight_satin_hull"),
            new Finish("seamed_padding", "seamed_padding"),
            new Finish("ribbed_padding", "ribbed_padding"),
            new Finish("stitched_padding", "stitched_padding")
    };
    public static final String[] IDS = new String[FINISHES.length];

    static {
        for (int i = 0; i < FINISHES.length; i++) IDS[i] = FINISHES[i].id;
    }

    public static final int INDUSTRIAL_BLOCK = 9;

    private ScreenHousingTextures() { }

    public static int clamp(int choice) {
        return choice >= 0 && choice < IDS.length ? choice : 0;
    }

    public static int cycle(int choice, int direction) {
        return Math.floorMod(clamp(choice) + direction, IDS.length);
    }

    public static String texture(int choice) {
        return "vandorlabs:blocks/" + FINISHES[clamp(choice)].texture;
    }
}
