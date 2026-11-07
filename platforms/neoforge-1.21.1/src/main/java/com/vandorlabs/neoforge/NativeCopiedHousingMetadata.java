package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.redstone.NativeTransmissionMigration;
import com.vandorlabs.persistence.FrameSettings;
import com.vandorlabs.redstone.*;
import net.minecraft.nbt.CompoundTag;

/** Bounded copied metadata updates preserve the installed host's unrelated and owned fields. */
public final class NativeCopiedHousingMetadata {
 /** Bounded read projection: never serialize inventory, attachments or unrelated retained compounds. */
 public static CompoundTag snapshot(com.vandorlabs.persistence.HousingSettings housing,CompoundTag retained){
  var out=new CompoundTag();housing.write(new ModernHousingTags(out));
  com.vandorlabs.persistence.FrameSettings.read(new ModernHousingTags(retained),com.vandorlabs.neoforge.shape.NativeWallEntity::validFrame,com.vandorlabs.neoforge.shape.NativeWallEntity::validSurface,ModernFinishCatalog::validChoice).write(new ModernHousingTags(out));
  ChannelList channels=ChannelList.of(Math.max(0,retained.getInt("RedstoneChannel")));if(retained.contains("RedstoneChannels",11))try{channels=ChannelList.of(retained.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){/* Released scalar fallback. */}out.putInt("RedstoneChannel",channels.first());out.putIntArray("RedstoneChannels",channels.toArray());
  var original=retained.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");var main=new CompoundTag();main.putInt("Range",TransmissionSettings.readRange(original.contains("Range",3),original.getInt("Range"),true));main.putBoolean("Local",TransmissionSettings.readLocal(original.contains("Local",1),original.getBoolean("Local"),false));main.putIntArray("Channels",TransmissionSettings.readChannels(original.contains("Channels",11),original.getIntArray("Channels"),ChannelList.EMPTY).toArray());var all=new CompoundTag();all.put("main",main);var forge=new CompoundTag();forge.put(TransmissionSettings.SETTINGS,all);out.put("ForgeData",forge);
  out.putBoolean("CeilingMounted",retained.getBoolean("CeilingMounted"));if(retained.contains("CeilingPosition",3))out.putInt("CeilingPosition",retained.getInt("CeilingPosition"));String wall=com.vandorlabs.persistence.SaveSchema.Screen.WALL_POSITION;if(retained.contains(wall))out.putInt(wall,retained.getInt(wall));return out;
 }
 public static boolean valid(FrameSettings frames,ChannelList channels,int range,ChannelList transmitted){return com.vandorlabs.neoforge.shape.NativeWallEntity.valid(frames)&&channels!=null&&transmitted!=null&&TransmissionSettings.validRange(range);}
 public static CompoundTag edit(CompoundTag current,FrameSettings frames,ChannelList channels,int range,boolean local,ChannelList transmitted){
  if(!valid(frames,channels,range,transmitted))throw new IllegalArgumentException("Invalid copied housing metadata");
  var migration=NativeTransmissionMigration.read(current);if(!migration.installable())throw new IllegalArgumentException("Unsupported installed transmission ownership");var next=migration.install();
  frames.write(new ModernHousingTags(next));next.putInt("RedstoneChannel",channels.first());next.putIntArray("RedstoneChannels",channels.toArray());
  var forge=next.getCompound("ForgeData");var settings=forge.getCompound(TransmissionSettings.SETTINGS);var main=new CompoundTag();main.putInt("Range",range);main.putBoolean("Local",local);main.putIntArray("Channels",transmitted.toArray());settings.put("main",main);forge.put(TransmissionSettings.SETTINGS,settings);next.put("ForgeData",forge);return next;
 }
 private NativeCopiedHousingMetadata(){}
}
