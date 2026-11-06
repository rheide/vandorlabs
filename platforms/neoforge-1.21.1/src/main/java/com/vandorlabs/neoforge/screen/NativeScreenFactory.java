package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.nbt.CompoundTag;
/** Exact initial device choices; a host installs these before publishing its first projection. */
public final class NativeScreenFactory {
 private NativeScreenFactory(){}
 public static CompoundTag choices(ScreenFamily family,NativeScreenSettings.Catalog catalog,int deviceHousing,int engineeringFrame){if(family==null||!catalog.validMaterial(deviceHousing)||!catalog.validMaterial(engineeringFrame))throw new IllegalArgumentException("Missing screen factory artwork");CompoundTag tag=new CompoundTag();NativeScreenSettings.fresh(catalog).write(tag);tag.putInt("housingTexture",catalog.material(deviceHousing));tag.putInt("HousingTextureVersion",1);if(family.displayArtwork){tag.putInt("FrameFront",catalog.material(engineeringFrame));tag.putInt("DisplayArtworkVersion",1);tag.putBoolean("framed",false);}return tag;}
}
