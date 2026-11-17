package com.vandorlabs.machines;
import java.util.*;
/** Appended data-driven recipe identity must not renumber released static blueprint indices. */
public final class BuilderRecipeCatalogTest {
 public static void main(String[] args){
  List<String> old=Arrays.asList("synthesis_tower_core","assembly_press_core","enclosed_gantry_core","standalone_antenna_core","standalone_dish_core","vector_fold_array_core","meridian_drive_core","longburn_drive_core","oculus_drive_core","vehicle_controller","programmable_ramp");
  List<String> current=BuilderRecipeCatalog.known("synthesis_tower");need(current.size()==old.size()+1&&current.subList(0,old.size()).equals(old),"Released recipe indices stay unchanged");need(current.get(old.size()).equals("digitized_block"),"Dynamic recipe appended last");
  need(!BuilderRecipeCatalog.defaults("synthesis_tower").contains("digitized_block"),"No default dynamic recipe without chip data");need(BuilderRecipeCatalog.available("synthesis_tower",Collections.singletonList("digitized_block")).equals(Arrays.asList("synthesis_tower_core","assembly_press_core","enclosed_gantry_core","digitized_block")),"Catalog selects stable dynamic placeholder");
  need(BuilderRecipeCatalog.available("assembly_press",Collections.singletonList("digitized_block")).equals(BuilderRecipeCatalog.defaults("assembly_press")),"Dynamic Tower recipe cannot enter Press");
  System.out.println("BuilderRecipeCatalogTest PASS releasedIndices/dynamicAppend/catalogSelection/machineIsolation");
 }
 private static void need(boolean value,String why){if(!value)throw new AssertionError(why);}
}
