package com.vandorlabs.neoforge.builders;
import net.minecraft.world.item.ItemStack;
/** Appended supported products preserve all released Tower recipe indices. */
public final class NativeBuilderProducts {
 public static final String[] IDS={"synthesis_tower","assembly_press","enclosed_gantry","standalone_antenna","standalone_dish"};
 public static final String[] NAMES={"Synthesis Tower","Assembly Press","Enclosed Gantry","Sentinel Array Core","Survey Dish Core"};
 public static ItemStack result(int index){if(index<0||index>=IDS.length)throw new IllegalArgumentException("Builder product");return new ItemStack(index<3?NativeBuilderBindings.CORES.get(IDS[index]):com.vandorlabs.neoforge.navigation.observer.NativeObserverBindings.CORES.get(IDS[index]));}
 public static boolean output(String id){for(String name:IDS)if(id.equals("vandorlabs:"+name+"_core"))return true;return false;}
 private NativeBuilderProducts(){}
}
