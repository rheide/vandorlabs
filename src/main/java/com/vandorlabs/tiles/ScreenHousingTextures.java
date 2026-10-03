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
            new Finish("modular_polished_metal_floor_1", "modular_polished_metal_floor_1"),
            new Finish("intricate_composite_wall_1", "intricate_composite_wall_1"),
            new Finish("intricate_composite_wall_2", "intricate_composite_wall_2"),
            new Finish("integrated_circuit_board_panel_1", "integrated_circuit_board_panel_1"),
            new Finish("heavy_duty_bulkhead_1", "heavy_duty_bulkhead_1"),
            new Finish("heavy_duty_bulkhead_2", "heavy_duty_bulkhead_2"),
            new Finish("non_glowing_data_core_cluster_1", "non_glowing_data_core_cluster_1"),
            new Finish("anti_gravity_control_panel_floor_1", "anti_gravity_control_panel_floor_1"),
            new Finish("thermal_shielding_panel_1", "thermal_shielding_panel_1"),
            new Finish("thermal_shielding_panel_2", "thermal_shielding_panel_2"),
            new Finish("composite_nano_fiber_hull_1", "composite_nano_fiber_hull_1"),
            new Finish("perforated_decking_1", "perforated_decking_1"),
            new Finish("greebled_tech_panel_1", "greebled_tech_panel_1"),
            new Finish("greebled_tech_panel_2", "greebled_tech_panel_2"),
            new Finish("advanced_biomechanical_tech_block_1", "advanced_biomechanical_tech_block_1"),
            new Finish("advanced_biomechanical_tech_block_2", "advanced_biomechanical_tech_block_2"),
            new Finish("textures2_t1_r1_c7", "textures2_t1_r1_c7"),
            new Finish("textures2_t1_r1_c8", "textures2_t1_r1_c8"),
            new Finish("textures2_t1_r3_c8", "textures2_t1_r3_c8"),
            new Finish("textures2_t1_r4_c1", "textures2_t1_r4_c1"),
            new Finish("textures2_t1_r4_c7", "textures2_t1_r4_c7"),
            new Finish("textures2_t1_r4_c8", "textures2_t1_r4_c8"),
            new Finish("textures2_t2_r1_c3", "textures2_t2_r1_c3"),
            new Finish("textures2_t2_r1_c4", "textures2_t2_r1_c4"),
            new Finish("textures2_t2_r3_c1", "textures2_t2_r3_c1"),
            new Finish("textures2_t2_r3_c2", "textures2_t2_r3_c2"),
            new Finish("textures2_t2_r3_c7", "textures2_t2_r3_c7"),
            new Finish("textures2_t2_r3_c8", "textures2_t2_r3_c8"),
            new Finish("textures2_t2_r4_c5", "textures2_t2_r4_c5"),
            new Finish("textures2_t3_r1_c7", "textures2_t3_r1_c7"),
            new Finish("textures2_t3_r1_c8", "textures2_t3_r1_c8"),
            new Finish("textures2_t3_r2_c2", "textures2_t3_r2_c2"),
            new Finish("textures2_t3_r3_c3", "textures2_t3_r3_c3"),
            new Finish("textures2_t3_r3_c5", "textures2_t3_r3_c5"),
            new Finish("textures2_t3_r3_c6", "textures2_t3_r3_c6"),
            new Finish("textures2_t4_r2_c7", "textures2_t4_r2_c7"),
            new Finish("textures2_t4_r2_c8", "textures2_t4_r2_c8"),
            new Finish("textures2_t4_r3_c5", "textures2_t4_r3_c5"),
            new Finish("textures2_t4_r3_c6", "textures2_t4_r3_c6"),
            new Finish("hull_plating_1", "hull_plating_1"),
            new Finish("hull_plating_2", "hull_plating_2"),
            new Finish("hull_plating_3", "hull_plating_3"),
            new Finish("hull_plating_4", "hull_plating_4"),
            new Finish("hull_plating_5", "hull_plating_5"),
            new Finish("hull_plating_6", "hull_plating_6"),
            new Finish("hull_plating_7", "hull_plating_7"),
            new Finish("hull_plating_8", "hull_plating_8"),
            new Finish("hull_plating_9", "hull_plating_9"),
            new Finish("hull_plating_10", "hull_plating_10"),
            new Finish("hull_plating_11", "hull_plating_11")
    };
    public static final String[] IDS;
    private static final String[] TEXTURES;
    private static final java.util.List<com.google.gson.JsonObject> EXTRAS=new java.util.ArrayList<>();
    public static final int LEGACY_COUNT=FINISHES.length;
    public static final int BUILTIN_COUNT;
    public static final int DEFAULT_TRAPDOOR;
    private static final java.util.Map<Integer,Integer> FILE_CHOICES=new java.util.HashMap<>();
    static {
        try(java.io.InputStream stream=ScreenHousingTextures.class.getResourceAsStream("/assets/vandorlabs/data/unified_textures.json")) {
            if(stream==null)throw new IllegalStateException("Shared texture catalog missing");
            for(com.google.gson.JsonElement e:new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(stream,"UTF-8")).getAsJsonArray())EXTRAS.add(e.getAsJsonObject());
        } catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
        BUILTIN_COUNT=FINISHES.length+EXTRAS.size();
        EXTRAS.addAll(FilesystemTextures.entries());
        IDS=new String[FINISHES.length+EXTRAS.size()];TEXTURES=new String[IDS.length];
        for(int i=0;i<FINISHES.length;i++){IDS[i]=FINISHES[i].id;TEXTURES[i]="vandorlabs:blocks/"+FINISHES[i].texture;}
        for(int i=0;i<EXTRAS.size();i++) {
            com.google.gson.JsonObject entry=EXTRAS.get(i);int index=i+FINISHES.length;
            IDS[index]=entry.get("id").getAsString();
            if(entry.has("key"))FILE_CHOICES.put(entry.get("key").getAsInt(),index);
            TEXTURES[index]="vandorlabs:blocks/"+(entry.has("rectangular")?"unified/"+IDS[index]:entry.get("source").getAsString());
        }
        DEFAULT_TRAPDOOR=screenIndex("imported/trapdoors/cyan_lit_armored_sci_fi_hatch_4");
        if(DEFAULT_TRAPDOOR==0)throw new IllegalStateException("Default trapdoor material missing");
    }
    public static com.google.gson.JsonObject entry(int choice){int index=localIndex(choice)-LEGACY_COUNT;return index>=0 && index<EXTRAS.size()?EXTRAS.get(index):null;}
    public static String category(int choice){com.google.gson.JsonObject e=entry(choice);return e!=null?e.get("category").getAsString():choice<28?"Materials":choice<44?"Texture Pack 1":choice<67?"Texture Pack 2":"Hull Plating";}
    public static String label(int choice){com.google.gson.JsonObject e=entry(choice);return e==null?null:e.get("label").getAsString();}
    public static int screenIndex(String source){for(int i=0;i<EXTRAS.size();i++)if(source.equals(EXTRAS.get(i).get("source").getAsString()))return LEGACY_COUNT+i;return 0;}
    public static boolean isDoor(int choice){if(CustomBlockMaterials.isCustom(choice))return com.vandorlabs.VandorLabs.proxy!=null && com.vandorlabs.VandorLabs.proxy.customDoor(choice);com.google.gson.JsonObject e=entry(choice);return e!=null && e.has("design");}
    public static int doorIndex(int design,int detail){return LEGACY_COUNT+6+design*3+detail;}
    /** Static Off artwork remains available as a material, but not in light menus. */
    public static boolean isLightOff(int choice){com.google.gson.JsonObject e=entry(choice);return e!=null && "Lights".equals(e.get("category").getAsString()) && e.get("id").getAsString().endsWith("_off");}
    public static int lightIndex(int style){return LEGACY_COUNT+Math.max(0,Math.min(5,style));}
    public static String texture(int choice,boolean lit){com.google.gson.JsonObject e=entry(choice);return !lit && e!=null && e.has("unlit")?"vandorlabs:blocks/"+e.get("unlit").getAsString():texture(choice);}

    public static final int INDUSTRIAL_BLOCK = 9;

    private ScreenHousingTextures() { }

    public static boolean validChoice(int choice){return choice>=0 && (choice<BUILTIN_COUNT || choice>=CustomBlockMaterials.ID_BASE);}
    public static int choiceAt(int index){com.google.gson.JsonObject e=entry(index);return e!=null && e.has("key")?e.get("key").getAsInt():index;}
    public static int localIndex(int choice){return CustomBlockMaterials.isCustom(choice)?0:choice>=FilesystemTextures.ID_BASE?FILE_CHOICES.getOrDefault(choice,0):choice>=0 && choice<IDS.length?choice:0;}

    public static int clamp(int choice) {
        return validChoice(choice) ? choice : 0;
    }

    public static int cycle(int choice, int direction) {
        return choiceAt(Math.floorMod(localIndex(clamp(choice)) + direction, IDS.length));
    }

    /** Full artwork for door leaves and thumbnails; ordinary blocks use one square half. */
    public static String fullTexture(int choice) {
        if(CustomBlockMaterials.isCustom(choice) && com.vandorlabs.VandorLabs.proxy!=null)return com.vandorlabs.VandorLabs.proxy.customTexture(choice);
        return TEXTURES[localIndex(choice)];
    }
    public static String texture(int choice) {
        if(CustomBlockMaterials.isCustom(choice) && com.vandorlabs.VandorLabs.proxy!=null)return com.vandorlabs.VandorLabs.proxy.customTexture(choice);
        com.google.gson.JsonObject e=entry(choice);
        return TEXTURES[localIndex(choice)]+(e!=null && e.has("design")?"_half":"");
    }
}
