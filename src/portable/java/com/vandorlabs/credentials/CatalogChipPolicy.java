package com.vandorlabs.credentials;
import java.util.*;
/** Portable bounded blueprint payload policy. Stored lists, rather than chip names, grant recipes. */
public final class CatalogChipPolicy {
 public static final int VERSION=1,MAX_DESIGNS=128,MAX_ID=64;
 public static final String[] MACHINES={"synthesis_tower","assembly_press","enclosed_gantry"};
 private static final String[][] MASTER={
  {"synthesis_tower_core","assembly_press_core","enclosed_gantry_core","sentinel_array_core","standalone_antenna_core","survey_dish_core","standalone_dish_core","vector_fold_array_core","meridian_drive_core","longburn_drive_core","oculus_drive_core","vehicle_controller","programmable_ramp"},
  {"data_chip","access_card","battery_module","autopilot_module"},
  {"gear_extender","gear_swing_arm","large_gear_extender","large_gear_swing_arm","landing_wheel","large_landing_wheel","landing_ski","landing_plate","square_coupler","vehicle_coupler_slab"}};
 public static boolean machine(String id){for(String machine:MACHINES)if(machine.equals(id))return true;return false;}
 public static boolean valid(String id){if(id==null||id.isEmpty()||id.length()>MAX_ID)return false;for(int i=0;i<id.length();i++){char c=id.charAt(i);if(!(c>='a'&&c<='z'||c>='0'&&c<='9'||c=='_'))return false;}return true;}
 public static List<String> master(String machine){for(int i=0;i<MACHINES.length;i++)if(MACHINES[i].equals(machine))return Collections.unmodifiableList(Arrays.asList(MASTER[i].clone()));return Collections.emptyList();}
 private CatalogChipPolicy(){}
}
