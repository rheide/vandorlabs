package com.vandorlabs.machines;

import java.util.*;

/** Stable blueprint identifiers. Catalog entries are data, independent of platform item registries. */
public final class BuilderRecipeCatalog {
    private static final List<String> TOWER=immutable("synthesis_tower_core","assembly_press_core","enclosed_gantry_core","standalone_antenna_core","standalone_dish_core","vector_fold_array_core","meridian_drive_core","longburn_drive_core","oculus_drive_core","vehicle_controller","programmable_ramp","digitized_block");
    private static final List<String> PRESS=immutable("data_chip","access_card","battery_module","autopilot_module","navigation_data_module","programmable_plate");
    private static final List<String> PRESS_DEFAULTS=immutable("data_chip","access_card","programmable_plate");
    private static final List<String> GANTRY=immutable("gear_extender","gear_swing_arm","large_gear_extender","large_gear_swing_arm","landing_wheel","large_landing_wheel","landing_ski","landing_plate","square_coupler","vehicle_coupler_slab");
    private static List<String> immutable(String... ids){return Collections.unmodifiableList(Arrays.asList(ids));}
    public static String canonical(String id){return "survey_dish_core".equals(id)?"standalone_dish_core":"sentinel_array_core".equals(id)?"standalone_antenna_core":id;}
    public static boolean machine(String id){return "synthesis_tower".equals(id)||"assembly_press".equals(id)||"enclosed_gantry".equals(id);}
    public static List<String> known(String machine){return "synthesis_tower".equals(machine)?TOWER:"assembly_press".equals(machine)?PRESS:"enclosed_gantry".equals(machine)?GANTRY:Collections.<String>emptyList();}
    public static List<String> defaults(String machine){return "synthesis_tower".equals(machine)?TOWER.subList(0,3):"assembly_press".equals(machine)?PRESS_DEFAULTS:known(machine);}
    public static String input(String machine){return "assembly_press".equals(machine)?"programmable_matter_ingot":"programmable_matter_block";}
    public static List<String> available(String machine,List<String> catalog){LinkedHashSet<String> ids=new LinkedHashSet<>(defaults(machine));for(String saved:catalog){String id=canonical(saved);if(known(machine).contains(id))ids.add(id);}return Collections.unmodifiableList(new ArrayList<>(ids));}
    private BuilderRecipeCatalog(){}
}
