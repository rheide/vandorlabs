package com.vandorlabs.neoforge.credentials;
import com.vandorlabs.credentials.CatalogChipPolicy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
/** Chip faces are separate from the preserved company credential component. */
public final class NativeChipTypes {
 public static CompoundTag data(ItemStack stack){var root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();return root.contains("ChipType")?root:root.getCompound(NativeCredentialItems.LEGACY_KEY).getCompound("tag");}
 public static String type(ItemStack stack){return data(stack).getString("ChipType");}
 public static int appearance(ItemStack stack){return switch(type(stack)){case "journal"->1;case "vehicle"->2;case "navigation"->3;case "catalog"->4;default->0;};}
 public static ItemStack variant(String type){var stack=NativeCredentialItems.dataChip(0);var tag=new CompoundTag();tag.putString("ChipType",type);if(type.equals("catalog")){tag.putString("CatalogName","Master");var payload=new CompoundTag();payload.putInt("Version",CatalogChipPolicy.VERSION);var designs=new CompoundTag();for(String machine:CatalogChipPolicy.MACHINES){var list=new ListTag();for(String id:CatalogChipPolicy.master(machine))list.add(StringTag.valueOf(id));designs.put(machine,list);}payload.put("Designs",designs);tag.put("Catalog",payload);}stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return stack;}
 private NativeChipTypes(){}
}
