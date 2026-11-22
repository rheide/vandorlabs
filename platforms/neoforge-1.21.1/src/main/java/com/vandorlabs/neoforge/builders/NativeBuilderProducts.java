package com.vandorlabs.neoforge.builders;
import com.vandorlabs.machines.BuilderRecipeCatalog;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
/** Appended supported products preserve all released Tower recipe indices. */
public final class NativeBuilderProducts {
 public static final String[] IDS={"synthesis_tower","assembly_press","enclosed_gantry","standalone_antenna","standalone_dish"};
 public static final String[] NAMES={"Synthesis Tower","Assembly Press","Enclosed Gantry","Sentinel Array Core","Survey Dish Core"};
 public static final int RAMP=6,LONGBURN=7,MERIDIAN=8,OCULUS=9,VECTOR_FOLD=10,VEHICLE_CONTROLLER=11,MAX_TOWER_PRODUCT=VEHICLE_CONTROLLER;
 public static final String RAMP_BLUEPRINT="programmable_ramp";
 public static boolean rampAvailable(){return com.vandorlabs.neoforge.ramp.NativeRampBindings.ITEM!=null;}
 public static String towerCore(int index){
  if(index>=0&&index<IDS.length)return IDS[index];
  if(index==LONGBURN)return NativeDriveDesign.LONGBURN.id;
  if(index==MERIDIAN)return NativeDriveDesign.MERIDIAN.id;
  if(index==OCULUS)return NativeDriveDesign.OCULUS.id;
  if(index==VECTOR_FOLD)return NativeDriveDesign.VECTOR_FOLD.id;
  return null;
 }
 public static String towerBlueprint(int index){
  if(index==RAMP)return RAMP_BLUEPRINT;
  if(index==VEHICLE_CONTROLLER)return "vehicle_controller";
  String core=towerCore(index);
  if(core==null)return null;
  var drive=NativeDriveDesign.of(core);
  return drive==null?core+"_core":drive.block;
 }
 public static ItemStack result(int index){
  if(index==VEHICLE_CONTROLLER)return new ItemStack(com.vandorlabs.neoforge.vehicle.NativeVehicleBindings.CONTROLLER);
  if(index==RAMP)return rampAvailable()?new ItemStack(com.vandorlabs.neoforge.ramp.NativeRampBindings.ITEM):ItemStack.EMPTY;String design=towerCore(index);
  if(design==null)throw new IllegalArgumentException("Builder product");
  var core=NativeBuilderBindings.CORES.get(design);
  return core!=null?new ItemStack(core):new ItemStack(com.vandorlabs.neoforge.navigation.observer.NativeObserverBindings.CORES.get(design));
 }
 // Index zero is the original native Press recipe, including in-progress saved work.
 public static final List<String> PRESS = List.of("navigation_data_module", "data_chip", "access_card",
         "battery_module", "autopilot_module", "programmable_plate");
 // Keep the native packet choices append-only. The final two Forge blueprints require Vehicle Mode.
 public static final List<String> GANTRY = List.of("gear_extender", "gear_swing_arm", "large_gear_extender",
         "large_gear_swing_arm", "landing_wheel", "large_landing_wheel", "landing_ski", "landing_plate");
 public static List<String> parts(String machine) {
  return switch(machine){
   case "assembly_press" -> PRESS;
   case "enclosed_gantry" -> GANTRY;
   default -> List.of();
  };
 }
 public static ItemStack partResult(String machine,int index) {
  return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",parts(machine).get(index))));
 }
 public static boolean partDefault(String machine,int index) {
  return BuilderRecipeCatalog.defaults(machine).contains(parts(machine).get(index));
 }
 public static boolean pressPartAvailable(int index) {
  String id=PRESS.get(index);
  return (!id.equals("battery_module")&&!id.equals("autopilot_module"))||com.vandorlabs.neoforge.rack.NativeRackBindings.enabled();
 }
 public static boolean output(String machine,String id) {
  if(!id.startsWith("vandorlabs:"))return false;
  String path=id.substring("vandorlabs:".length());
  if(machine.equals("synthesis_tower")){
   if(path.equals(RAMP_BLUEPRINT))return rampAvailable();
   for(int index=0;index<=MAX_TOWER_PRODUCT;index++)if(path.equals(towerBlueprint(index)))return true;
   return false;
  }
  return parts(machine).contains(path);
 }
 private NativeBuilderProducts(){}
}
