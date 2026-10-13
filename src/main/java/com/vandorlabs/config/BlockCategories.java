package com.vandorlabs.config;

/** Stable category keys shared by configuration, menus and recipes. */
public final class BlockCategories {
    public static final String[] IDS={"screens","consoles_inputs","programmable_solids","diagonals",
            "glass","doors","ramps","lighting","seating","vehicle_controls","propulsion",
            "canopies","redstone_controls","machinery","decoration"};
    private BlockCategories() {}
    public static String of(String id) {
        if(id.startsWith("hadron_"))return "machinery";
        if(id.contains("diagonal"))return "diagonals";
        if(id.contains("canopy")||id.startsWith("space_visor")||id.startsWith("space_wedge")
                ||id.startsWith("space_longnose")||id.startsWith("space_crystal")||id.startsWith("space_shuttle")
                ||id.startsWith("space_bubble")||id.startsWith("visor_module")
                ||id.startsWith("angled_front")||id.startsWith("opening_"))return "canopies";
        if(id.contains("glass"))return "glass";
        if(id.contains("door"))return "doors";
        if(id.contains("ramp"))return "ramps";
        if(id.equals("pilot_seat")||id.equals("landing_gear"))return "vehicle_controls";
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
