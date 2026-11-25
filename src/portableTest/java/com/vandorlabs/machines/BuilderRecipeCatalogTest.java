package com.vandorlabs.machines;
import java.util.*;
/** Appended data-driven recipe identity must not renumber released static blueprint indices. */
public final class BuilderRecipeCatalogTest {
 public static void main(String[] args){
  List<String> old=Arrays.asList("synthesis_tower_core","assembly_press_core","enclosed_gantry_core","standalone_antenna_core","standalone_dish_core","vector_fold_array_core","meridian_drive_core","longburn_drive_core","oculus_drive_core","vehicle_controller","programmable_ramp");
  List<String> current=BuilderRecipeCatalog.known("synthesis_tower");need(current.size()==old.size()+1&&current.subList(0,old.size()).equals(old),"Released recipe indices stay unchanged");need(current.get(old.size()).equals("digitized_block"),"Dynamic recipe appended last");
  need(!BuilderRecipeCatalog.defaults("synthesis_tower").contains("digitized_block"),"No default dynamic recipe without chip data");need(BuilderRecipeCatalog.available("synthesis_tower",Collections.singletonList("digitized_block")).equals(Arrays.asList("synthesis_tower_core","assembly_press_core","enclosed_gantry_core","digitized_block")),"Catalog selects stable dynamic placeholder");
  need(BuilderRecipeCatalog.available("assembly_press",Collections.singletonList("digitized_block")).equals(BuilderRecipeCatalog.defaults("assembly_press")),"Dynamic Tower recipe cannot enter Press");
  need(BuilderRecipeCatalog.known("assembly_press").equals(Arrays.asList("data_chip","access_card","battery_module",
          "autopilot_module","navigation_data_module","programmable_plate")),"Press preserves saved indices and appends Programmable Plate");
  need(BuilderRecipeCatalog.defaults("assembly_press").contains("programmable_plate"),"Plate is a default blueprint");
  need(BuilderRecipeCatalog.input("assembly_press").equals("programmable_matter_ingot")
          &&BuilderRecipeCatalog.input("synthesis_tower").equals("programmable_block")
          &&BuilderRecipeCatalog.input("enclosed_gantry").equals("programmable_block"),"Machine inputs remain distinct");
  List<String> gantry=Arrays.asList("gear_extender","gear_swing_arm","large_gear_extender","large_gear_swing_arm","landing_wheel","large_landing_wheel","landing_ski","landing_plate","square_coupler","vehicle_coupler_slab");
  List<String> propulsion=Arrays.asList("rocket_thruster","ion_drive","plasma_vent","impulse_engine","antigravity_plate","repulsor_array","vertical_hover_thruster");
  List<String> currentGantry=BuilderRecipeCatalog.known("enclosed_gantry");
  need(currentGantry.size()==gantry.size()+propulsion.size()&&currentGantry.subList(0,gantry.size()).equals(gantry)&&currentGantry.subList(gantry.size(),currentGantry.size()).equals(propulsion),"Gantry keeps released indices and appends thrusters and hover blocks");
  need(BuilderRecipeCatalog.defaults("enclosed_gantry").containsAll(propulsion),"Thrusters and hover blocks need no Catalog Chip");
  need(com.vandorlabs.credentials.CatalogChipPolicy.master("enclosed_gantry").equals(currentGantry),"Master Catalog lists every Gantry design");
  System.out.println("BuilderRecipeCatalogTest PASS releasedIndices/dynamicAppend/catalogSelection/machineIsolation/programmablePlate/gantryPropulsion");
 }
 private static void need(boolean value,String why){if(!value)throw new AssertionError(why);}
}
