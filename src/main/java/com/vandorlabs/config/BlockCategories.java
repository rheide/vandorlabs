package com.vandorlabs.config;

/** Stable category keys shared by configuration, menus and recipes. */
public final class BlockCategories {
    public static final String[] IDS={"screens","consoles_inputs","programmable_solids","diagonals",
            "glass","doors","ramps","lighting","seating","vehicle_controls","propulsion",
            "canopies","redstone_controls","decoration"};
    private BlockCategories() {}
    public static String of(String id) {
        if(id.equals("landing_wheel")||id.equals("large_landing_wheel")||id.equals("large_gear_extender")||id.equals("large_gear_swing_arm")||id.equals("landing_plate")||id.equals("landing_ski")||id.equals("gear_extender")||id.equals("gear_swing_arm"))return "vehicle_controls";
        if(id.contains("diagonal"))return "diagonals";
        if(id.contains("canopy")||id.startsWith("space_visor")||id.startsWith("angled_front"))return "canopies";
        if(id.contains("glass"))return "glass";
        if(id.contains("door"))return "doors";
        if(id.contains("ramp")||id.startsWith("turntable_"))return "ramps";
        if(id.startsWith("cockpit_")||id.equals("chair_siderests")||id.equals("yoke_screen")||id.equals("vehicle_controller")||id.equals("pilot_seat")||id.contains("landing_gear")||id.contains("landing_strut")||id.startsWith("wheel_"))return "vehicle_controls";
        if(id.contains("chair")||id.contains("seat"))return "seating";
        if(id.startsWith("rocket_thruster")||id.startsWith("ion_drive")||id.startsWith("plasma_vent")
                ||id.startsWith("impulse_engine")||id.equals("antigravity_plate")
                ||id.equals("repulsor_array")||id.equals("vertical_hover_thruster"))return "propulsion";
        if(id.contains("console")||id.contains("input"))return "consoles_inputs";
        if(id.contains("screen")||id.equals("programmable_viewscreen"))return "screens";
        if(id.startsWith("programmable_light"))return "lighting";
        if(id.startsWith("programmable_")&&!id.equals("programmable_trigger_block"))return "programmable_solids";
        if(id.contains("lever")||id.contains("switch")||id.contains("button")||id.contains("throttle")
                ||id.equals("wall_slider")||id.equals("programmable_trigger_block"))return "redstone_controls";
        return "decoration";
    }
}
