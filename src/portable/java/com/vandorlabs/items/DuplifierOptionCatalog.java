package com.vandorlabs.items;

/** Stable Copifier dialog ordering and semantic keys. Append choices to preserve saved bit indices. */
public final class DuplifierOptionCatalog {
    public static final class Option {
        public final String key,label;
        public final int page;
        private Option(String key,String label,int page){this.key=key;this.label=label;this.page=page;}
    }
    private static final String[] PAGES={"Common","Displays","Blocks","Doors","Ramps"};
    private static final Option[] OPTIONS={
        new Option("primary_texture","Primary Texture",0),
        new Option("wall_texture","Wall Texture",0),
        new Option("join","Join",0),
        new Option("redstone_channel","Redstone Channels",0),
        new Option("redstone_trigger","Redstone Trigger",0),
        new Option("active","Active State",0),
        new Option("particles","Particle Stream",0),
        new Option("framed","Frame",0),
        new Option("glass_shade","Glass Shade",0),
        new Option("light_level","Light Level",0),
        new Option("display_mode","Display Mode",1),
        new Option("animation_speed","Animation Speed",1),
        new Option("input_panel","Input Panel",1),
        new Option("secondary_input_panel","Second Input Panel",1),
        new Option("small_input","Small Input",1),
        new Option("wall_position","Wall Pos",1),
        new Option("trigger_on_texture","Trigger On Texture",1),
        new Option("propulsion_shape","Thruster Shape",2),
        new Option("porthole_shape","Porthole Shape",2),
        new Option("glass_size","Glass Size",2),
        new Option("side_texture","Side Texture",2),
        new Option("slab_tile_sides","Texture Layout",2),
        new Option("chair_style","Chair Style",2),
        new Option("chair_height","Chair Height",2),
        new Option("switch_rotation","Switch Rotation",2),
        new Option("door_design","Door Design",3),
        new Option("door_detail","Door Detail",3),
        new Option("door_slide_direction","Slide Direction",3),
        new Option("door_middle","Middle Door",3),
        new Option("door_sliding","Door / Trapdoor Movement",3),
        new Option("door_hinges","Door Hinges",3),
        new Option("door_panel","Door Panel",3),
        new Option("door_depth","Door Depth",3),
        new Option("ramp_start","Start Offset",4),
        new Option("ramp_end","End Offset",4),
        new Option("ramp_tread_pixels","Tread Size",4),
        new Option("ramp_speed","Ramp Speed",4),
        new Option("ramp_lift","Lift Mode",4),
        new Option("ramp_extend","Extend Mode",4),
        new Option("ramp_direction","Ramp Direction",4),
        new Option("ramp_travel","Travel Direction",4),
        new Option("face_textures","Face Overrides",2),
        new Option("diagonal_geometry","Diagonal Geometry",2),
        new Option("ramp_match_textures","Match Textures",4),
        new Option("gear_size","Gear Size",2),
        new Option("gear_length","Gear Extension",2),
        new Option("gear_mode","Gear Redstone Mode",2),
        new Option("trapdoor_position","Trapdoor Pos",3),
        new Option("trapdoor_cover","Next-block Leaf",3),
        new Option("primary_surface","Static Surface Texture",1),
        new Option("secondary_surface","Second Static Texture",1),
        new Option("light_face_texture","Light Face Texture",0),
        new Option("door_face_texture","Door Face Texture",3),
        new Option("door_tile_texture","Door Face Layout",3),
        new Option("trapdoor_tile_texture","Trapdoor Face Layout",3),
        new Option("trapdoor_cover_facing","Trapdoor Hinge Direction",3),
        new Option("redstone_rows","Redstone Screen Items",1),
        new Option("ramp_interpolation","Ramp Interpolation",4),
        new Option("signal_settings","Signal Levels",0),
        new Option("armorAppearance","Armor Texture",0),
        new Option("ramp_escalator","Escalator Mode",4),
        new Option("glass_tint","Glass Tint",2),
        new Option("glass_material","Glass Material / Layout",0),
        new Option("block_configuration","Canopy / Pilot Settings",2),
        new Option("MoatMode","Moat Mode",4),
        new Option("door_security","Door Security",3),
        new Option("stair_tread","Stair step size",2),
        new Option("stair_roll","Stair orientation",2),
        new Option("FrameAppearance","Frame Finishes",0),
        new Option("DoorGlassAppearance","Door Glass",3),
        new Option("modular_arm_settings","Arm / Track Settings",2),
        new Option("chair_angle","Chair Angle",2),
        new Option("ramp_vertical_layout","Vertical Ramp Layout",4),
        new Option("segmented_door","Segmented Door Settings",3)
    };
    public static String[] pages(){return PAGES.clone();}
    public static Option[] options(){return OPTIONS.clone();}
    public static int size(){return OPTIONS.length;}
    private DuplifierOptionCatalog(){}
}
