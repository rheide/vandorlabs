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
 public static ItemStack result(int index){if(index<0||index>=IDS.length)throw new IllegalArgumentException("Builder product");return new ItemStack(index<3?NativeBuilderBindings.CORES.get(IDS[index]):com.vandorlabs.neoforge.navigation.observer.NativeObserverBindings.CORES.get(IDS[index]));}
 // Index zero is the original native Press recipe, including in-progress saved work.
 public static final List<String> PRESS = List.of("navigation_data_module", "data_chip", "access_card",
         "battery_module", "autopilot_module");
 public static ItemStack pressResult(int index) {
  return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vandorlabs", PRESS.get(index))));
 }
 public static boolean pressDefault(int index) {
  return BuilderRecipeCatalog.defaults("assembly_press").contains(PRESS.get(index));
 }
 public static boolean pressOutput(String id) {
  return id.startsWith("vandorlabs:") && PRESS.contains(id.substring("vandorlabs:".length()));
 }
 public static boolean output(String id){for(String name:IDS)if(id.equals("vandorlabs:"+name+"_core"))return true;return false;}
 private NativeBuilderProducts(){}
}
