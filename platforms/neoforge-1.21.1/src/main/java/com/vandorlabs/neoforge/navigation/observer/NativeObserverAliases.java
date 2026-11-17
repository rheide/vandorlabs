package com.vandorlabs.neoforge.navigation.observer;
import java.util.Map;import net.minecraft.core.registries.BuiltInRegistries;import net.minecraft.nbt.*;import net.minecraft.resources.ResourceLocation;
/** Explicit released observer aliases; unrelated names and supplied properties remain untouched. */
public final class NativeObserverAliases {
 private static final Map<String,String> IDS=Map.of("vandorlabs:survey_dish_core","vandorlabs:standalone_dish_core","vandorlabs:sentinel_array_core","vandorlabs:standalone_antenna_core");
 public static CompoundTag palette(CompoundTag input){String target=IDS.get(input.getString("Name"));if(target==null)return input;var from=ResourceLocation.parse(input.getString("Name"));var to=ResourceLocation.parse(target);if(!BuiltInRegistries.BLOCK.resolve(from).equals(to)||!BuiltInRegistries.BLOCK.containsKey(to)||BuiltInRegistries.BLOCK.get(from)!=BuiltInRegistries.BLOCK.get(to))throw new IllegalArgumentException("Observer alias is not installed");var result=input.copy();result.putString("Name",target);return result;}
 /** The first upright observer saves predate the optional inversion flag. */
 public static CompoundTag tile(CompoundTag raw){if(!raw.getString("id").equals("vandorlabs:observer_core")||raw.contains("ObserverInverted"))return raw;var result=raw.copy();result.putBoolean("ObserverInverted",false);return result;}
 private NativeObserverAliases(){}
}
