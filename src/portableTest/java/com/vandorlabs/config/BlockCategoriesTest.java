package com.vandorlabs.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Category precedence is shared by configuration, menus and recipe filtering. */
public final class BlockCategoriesTest {
    private BlockCategoriesTest() {}

    private static void category(String id, String expected) {
        String actual = BlockCategories.of(id);
        if (!expected.equals(actual)) {
            throw new AssertionError(id + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        String[] expected = {"screens", "consoles_inputs", "programmable_solids", "diagonals",
                "glass", "doors", "ramps", "lighting", "seating", "vehicle_controls",
                "propulsion", "canopies", "redstone_controls", "decoration"};
        if (!Arrays.equals(expected, BlockCategories.IDS)) {
            throw new AssertionError("Stable category keys or order changed");
        }
        Set<String> categories = new HashSet<String>(Arrays.asList(BlockCategories.IDS));
        if (categories.size() != expected.length) {
            throw new AssertionError("Duplicate category key");
        }
        category("large_gear_swing_arm", "vehicle_controls");
        category("diagonal_glass", "diagonals");
        category("glass_canopy", "canopies");
        category("programmable_glass", "glass");
        category("programmable_door", "doors");
        category("turntable_controller", "ramps");
        category("cockpit_screen", "vehicle_controls");
        category("chair_siderests", "vehicle_controls");
        category("chair", "seating");
        category("ion_drive", "propulsion");
        category("screen_console", "consoles_inputs");
        category("programmable_viewscreen", "screens");
        category("programmable_light", "lighting");
        category("programmable_block", "programmable_solids");
        category("programmable_trigger_block", "redstone_controls");
        category("wall_slider", "redstone_controls");
        category("unknown_future_block", "decoration");
        System.out.println("Block category contracts PASS");
    }
}
