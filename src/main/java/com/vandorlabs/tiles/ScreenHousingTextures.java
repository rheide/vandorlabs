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
            new Finish("stitched_padding", "stitched_padding"),
            new Finish("glass_frame_inner", "programmable_glass/metal_side"),
            new Finish("door_inner", "programmable_glass/door_side"),
            new Finish("brushed_titanium_plating_1", "brushed_titanium_plating_1"),
            new Finish("brushed_titanium_plating_2", "brushed_titanium_plating_2"),
            new Finish("modular_polished_metal_floor_1", "modular_polished_metal_floor_1"),
            new Finish("modular_polished_metal_floor_2", "modular_polished_metal_floor_2"),
            new Finish("intricate_composite_wall_1", "intricate_composite_wall_1"),
            new Finish("intricate_composite_wall_2", "intricate_composite_wall_2"),
            new Finish("integrated_circuit_board_panel_1", "integrated_circuit_board_panel_1"),
            new Finish("integrated_circuit_board_panel_2", "integrated_circuit_board_panel_2"),
            new Finish("layered_ablative_shielding_1", "layered_ablative_shielding_1"),
            new Finish("layered_ablative_shielding_2", "layered_ablative_shielding_2"),
            new Finish("precision_cut_metal_grating_1", "precision_cut_metal_grating_1"),
            new Finish("precision_cut_metal_grating_2", "precision_cut_metal_grating_2"),
            new Finish("heavy_duty_bulkhead_1", "heavy_duty_bulkhead_1"),
            new Finish("heavy_duty_bulkhead_2", "heavy_duty_bulkhead_2"),
            new Finish("non_glowing_data_core_cluster_1", "non_glowing_data_core_cluster_1"),
            new Finish("non_glowing_data_core_cluster_2", "non_glowing_data_core_cluster_2"),
            new Finish("reinforced_bulkhead_panel_1", "reinforced_bulkhead_panel_1"),
            new Finish("reinforced_bulkhead_panel_2", "reinforced_bulkhead_panel_2"),
            new Finish("anti_gravity_control_panel_floor_1", "anti_gravity_control_panel_floor_1"),
            new Finish("anti_gravity_control_panel_floor_2", "anti_gravity_control_panel_floor_2"),
            new Finish("thermal_shielding_panel_1", "thermal_shielding_panel_1"),
            new Finish("thermal_shielding_panel_2", "thermal_shielding_panel_2"),
            new Finish("non_glowing_power_conduit_1", "non_glowing_power_conduit_1"),
            new Finish("non_glowing_power_conduit_2", "non_glowing_power_conduit_2"),
            new Finish("composite_nano_fiber_hull_1", "composite_nano_fiber_hull_1"),
            new Finish("composite_nano_fiber_hull_2", "composite_nano_fiber_hull_2"),
            new Finish("perforated_decking_1", "perforated_decking_1"),
            new Finish("perforated_decking_2", "perforated_decking_2"),
            new Finish("greebled_tech_panel_1", "greebled_tech_panel_1"),
            new Finish("greebled_tech_panel_2", "greebled_tech_panel_2"),
            new Finish("advanced_biomechanical_tech_block_1", "advanced_biomechanical_tech_block_1"),
            new Finish("advanced_biomechanical_tech_block_2", "advanced_biomechanical_tech_block_2"),
            new Finish("textures2_t1_r1_c1", "textures2_t1_r1_c1"),
            new Finish("textures2_t1_r1_c2", "textures2_t1_r1_c2"),
            new Finish("textures2_t1_r1_c3", "textures2_t1_r1_c3"),
            new Finish("textures2_t1_r1_c4", "textures2_t1_r1_c4"),
            new Finish("textures2_t1_r1_c6", "textures2_t1_r1_c6"),
            new Finish("textures2_t1_r1_c7", "textures2_t1_r1_c7"),
            new Finish("textures2_t1_r1_c8", "textures2_t1_r1_c8"),
            new Finish("textures2_t1_r2_c5", "textures2_t1_r2_c5"),
            new Finish("textures2_t1_r3_c1", "textures2_t1_r3_c1"),
            new Finish("textures2_t1_r3_c2", "textures2_t1_r3_c2"),
            new Finish("textures2_t1_r3_c8", "textures2_t1_r3_c8"),
            new Finish("textures2_t1_r4_c1", "textures2_t1_r4_c1"),
            new Finish("textures2_t1_r4_c2", "textures2_t1_r4_c2"),
            new Finish("textures2_t1_r4_c3", "textures2_t1_r4_c3"),
            new Finish("textures2_t1_r4_c4", "textures2_t1_r4_c4"),
            new Finish("textures2_t1_r4_c5", "textures2_t1_r4_c5"),
            new Finish("textures2_t1_r4_c7", "textures2_t1_r4_c7"),
            new Finish("textures2_t1_r4_c8", "textures2_t1_r4_c8"),
            new Finish("textures2_t2_r1_c3", "textures2_t2_r1_c3"),
            new Finish("textures2_t2_r1_c4", "textures2_t2_r1_c4"),
            new Finish("textures2_t2_r3_c1", "textures2_t2_r3_c1"),
            new Finish("textures2_t2_r3_c2", "textures2_t2_r3_c2"),
            new Finish("textures2_t2_r3_c5", "textures2_t2_r3_c5"),
            new Finish("textures2_t2_r3_c7", "textures2_t2_r3_c7"),
            new Finish("textures2_t2_r3_c8", "textures2_t2_r3_c8"),
            new Finish("textures2_t2_r4_c1", "textures2_t2_r4_c1"),
            new Finish("textures2_t2_r4_c2", "textures2_t2_r4_c2"),
            new Finish("textures2_t2_r4_c3", "textures2_t2_r4_c3"),
            new Finish("textures2_t2_r4_c4", "textures2_t2_r4_c4"),
            new Finish("textures2_t2_r4_c5", "textures2_t2_r4_c5"),
            new Finish("textures2_t2_r4_c7", "textures2_t2_r4_c7"),
            new Finish("textures2_t3_r1_c7", "textures2_t3_r1_c7"),
            new Finish("textures2_t3_r1_c8", "textures2_t3_r1_c8"),
            new Finish("textures2_t3_r2_c2", "textures2_t3_r2_c2"),
            new Finish("textures2_t3_r2_c3", "textures2_t3_r2_c3"),
            new Finish("textures2_t3_r2_c5", "textures2_t3_r2_c5"),
            new Finish("textures2_t3_r3_c3", "textures2_t3_r3_c3"),
            new Finish("textures2_t3_r3_c4", "textures2_t3_r3_c4"),
            new Finish("textures2_t3_r3_c5", "textures2_t3_r3_c5"),
            new Finish("textures2_t3_r3_c6", "textures2_t3_r3_c6"),
            new Finish("textures2_t3_r4_c5", "textures2_t3_r4_c5"),
            new Finish("textures2_t4_r2_c5", "textures2_t4_r2_c5"),
            new Finish("textures2_t4_r2_c7", "textures2_t4_r2_c7"),
            new Finish("textures2_t4_r2_c8", "textures2_t4_r2_c8"),
            new Finish("textures2_t4_r3_c5", "textures2_t4_r3_c5"),
            new Finish("textures2_t4_r3_c6", "textures2_t4_r3_c6")
    };
    public static final String[] IDS = new String[FINISHES.length];
    private static final String[] TEXTURES = new String[FINISHES.length];

    static {
        for (int i = 0; i < FINISHES.length; i++) {
            IDS[i] = FINISHES[i].id;
            TEXTURES[i] = "vandorlabs:blocks/" + FINISHES[i].texture;
        }
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
        return TEXTURES[clamp(choice)];
    }
}
