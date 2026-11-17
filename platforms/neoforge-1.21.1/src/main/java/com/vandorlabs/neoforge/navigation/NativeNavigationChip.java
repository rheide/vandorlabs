package com.vandorlabs.neoforge.navigation;

import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.navigation.NavigationMap;
import com.vandorlabs.navigation.SavedMapPixels;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Portable location payload; coordinates are block coordinates, including the reserved Y value. */
public final class NativeNavigationChip {
 public static String dimension(Level level){return level.dimension().location().toString();}
 public static String dimension(CompoundTag data){if(data.contains("DimensionKey",Tag.TAG_STRING))return data.getString("DimensionKey");if(data.contains("Dimension",Tag.TAG_INT))return switch(data.getInt("Dimension")){case 0->"minecraft:overworld";case -1->"minecraft:the_nether";case 1->"minecraft:the_end";default->"legacy:"+data.getInt("Dimension");};return "";}
 public static boolean item(ItemStack stack){return NativeCredentialItems.accepts(stack,true);}
 private static CompoundTag dataView(ItemStack stack){var root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe();return root.contains("ChipType")?root:root.getCompound(NativeCredentialItems.LEGACY_KEY).getCompound("tag");}
 private static CompoundTag locationView(ItemStack stack){return dataView(stack).getCompound("NavigationLocation");}
 public static CompoundTag location(ItemStack stack){return locationView(stack).copy();}
 public static boolean programmed(ItemStack stack){var t=locationView(stack);return item(stack)&&dataView(stack).getString("ChipType").equals("navigation")&&!dimension(t).isEmpty()&&t.contains("X",3)&&t.contains("Y",3)&&t.contains("Z",3)&&Math.abs((long)t.getInt("X"))<=NavigationMap.LIMIT&&Math.abs((long)t.getInt("Z"))<=NavigationMap.LIMIT;}
 public static boolean inDimension(ItemStack stack,Level level){return programmed(stack)&&dimension(locationView(stack)).equals(dimension(level));}
 public static void write(ItemStack stack,Level level,int x,int z){if(!item(stack)||Math.abs((long)x)>NavigationMap.LIMIT||Math.abs((long)z)>NavigationMap.LIMIT)throw new IllegalArgumentException("Navigation chip location");var root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();var tag=dataView(stack).copy();var point=new CompoundTag();point.putString("DimensionKey",dimension(level));point.putInt("X",x);point.putInt("Y",programmed(stack)?locationView(stack).getInt("Y"):64);point.putInt("Z",z);tag.remove("NavigationMapPixels");root.remove("NavigationMapPixels");for(String key:new String[]{"JournalTitle","JournalPages","JournalFont","VehicleDesign","VehicleDesignCodec","VehicleShape","VehicleName","Catalog","CatalogName"}){tag.remove(key);root.remove(key);}tag.putString("ChipType","navigation");tag.put("NavigationLocation",point);root.merge(tag);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));}
 public static SavedMapPixels savedMap(ItemStack stack){
  if(!programmed(stack)||!dataView(stack).contains("NavigationMapPixels",Tag.TAG_COMPOUND))return null;var t=dataView(stack).getCompound("NavigationMapPixels");
  for(String key:new String[]{"Version","CenterX","CenterZ","Scale","Hash"})if(!t.contains(key,Tag.TAG_INT))return null;
  if(t.getInt("Version")!=1||!t.contains("Colors",Tag.TAG_BYTE_ARRAY)||!t.contains("Palette",Tag.TAG_INT_ARRAY))return null;
  try{var map=new SavedMapPixels(dimension(locationView(stack)),t.getInt("CenterX"),t.getInt("CenterZ"),t.getInt("Scale"),t.getByteArray("Colors"),t.getIntArray("Palette"));return map.hash()==t.getInt("Hash")&&map.centerX()==locationView(stack).getInt("X")&&map.centerZ()==locationView(stack).getInt("Z")?map:null;}catch(IllegalArgumentException invalid){return null;}
 }
 public static void writeMap(ItemStack stack,SavedMapPixels map){
  if(!item(stack))throw new IllegalArgumentException("A data chip is required");var root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();var tag=dataView(stack).copy();var point=new CompoundTag();point.putString("DimensionKey",map.dimension());point.putInt("X",map.centerX());point.putInt("Y",programmed(stack)?locationView(stack).getInt("Y"):64);point.putInt("Z",map.centerZ());for(String key:new String[]{"JournalTitle","JournalPages","JournalFont","VehicleDesign","VehicleDesignCodec","VehicleShape","VehicleName","Catalog","CatalogName"}){tag.remove(key);root.remove(key);}tag.putString("ChipType","navigation");tag.put("NavigationLocation",point);var t=new CompoundTag();t.putInt("Version",1);t.putInt("CenterX",map.centerX());t.putInt("CenterZ",map.centerZ());t.putInt("Scale",map.scale());t.putByteArray("Colors",map.colors());t.putIntArray("Palette",map.palette());t.putInt("Hash",map.hash());tag.put("NavigationMapPixels",t);root.merge(tag);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));
 }
 /** Public update projection only; original inventory/save data stays intact. */
 public static ItemStack summary(ItemStack stack){var copy=stack.copy();var root=copy.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();stripPixels(root);stripPixels(root.getCompound(NativeCredentialItems.LEGACY_KEY).getCompound("tag"));copy.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return copy;}
 private static void stripPixels(CompoundTag root){if(root.contains("NavigationMapPixels",Tag.TAG_COMPOUND)){var t=root.getCompound("NavigationMapPixels");t.remove("Colors");t.remove("Palette");}}
 private NativeNavigationChip(){}
}
