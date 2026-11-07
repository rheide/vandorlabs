package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.redstone.NativeChannelMember;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Semantic control clipboard. Runtime latch banks, received signals and opaque host data stay local. */
public final class NativeCopifierControlEdit {
 public static boolean applicable(BlockEntity tile,CompoundTag values){
  if(values==null||values.isEmpty()||!NativeCopiedSettingsValidation.valid(values))return false;
  boolean signal=values.contains("signal_settings",Tag.TAG_COMPOUND);
  return values.contains("redstone_channel")||(tile instanceof NativeBinaryControlBlockEntity||tile instanceof NativeWirelessRedstoneBlockEntity wireless&&wireless.transmitter())&&values.contains("Transmission",Tag.TAG_COMPOUND)||values.contains("switch_rotation",Tag.TAG_INT)||values.contains("active",Tag.TAG_BYTE)
   ||tile instanceof NativeBinaryControlBlockEntity&&(signal||tile instanceof NativeSignalControlBlockEntity&&values.contains("active",Tag.TAG_INT))
   ||tile instanceof NativeWirelessRedstoneBlockEntity&&signal&&values.getCompound("signal_settings").contains("WirelessAnalog");
 }
 private static ChannelList receive(CompoundTag values,ChannelList current){
  if(values.contains("redstone_channel",Tag.TAG_INT_ARRAY))try{return ChannelList.of(values.getIntArray("redstone_channel"));}catch(IllegalArgumentException malformed){return current;}
  return values.contains("redstone_channel",Tag.TAG_INT)?ChannelList.of(Math.max(0,values.getInt("redstone_channel"))):current;
 }
 /** Caller validates and records undo before entering the existing signal-update batch. */
 public static void apply(BlockEntity tile,CompoundTag values){
  var settings=values.getCompound("signal_settings");boolean signal=values.contains("signal_settings",Tag.TAG_COMPOUND),transmit=values.contains("Transmission",Tag.TAG_COMPOUND)&&(!(tile instanceof NativeWirelessRedstoneBlockEntity wireless)||wireless.transmitter());var transmission=values.getCompound("Transmission");
  if(tile instanceof NativeBinaryControlBlockEntity control){
   if(control instanceof NativeSignalControlBlockEntity numeric){
    if(signal){numeric.configureCopiedLimits(settings.getInt("LowLimit"),settings.getInt("HighLimit"));if(settings.contains("BaseHeight")){if(settings.contains("ControlType",Tag.TAG_INT))numeric.configureType(settings.getInt("ControlType"));var current=control.mountSettings();control.configureMount(ControlMountSettings.read(new ModernHousingTags(settings),current.height,current.tilt,current.direction));}}
    if(values.contains("active",Tag.TAG_INT))numeric.setStep(values.getInt("active"));
   }else if(signal){var current=control.mountSettings();control.configureMount(ControlMountSettings.read(new ModernHousingTags(settings),current.height,current.tilt,current.direction));if(control.supportsPowerLeverSize()&&settings.contains("PowerLeverSize",Tag.TAG_INT))control.setPowerLeverSize(settings.getInt("PowerLeverSize"));}
   if(values.contains("active",Tag.TAG_BYTE))control.setLocalOn(values.getBoolean("active"));
   if(values.contains("switch_rotation",Tag.TAG_INT))control.configureCopiedRotation(values.getInt("switch_rotation"));
   if(values.contains("redstone_channel")||transmit)control.configureTransmission(transmit?ChannelList.of(transmission.getIntArray("Channels")):control.transmissionChannels(),transmit?transmission.getInt("Range"):control.transmissionRange(),transmit?transmission.getBoolean("Local"):control.localRedstone());
  }else if(tile instanceof NativeWirelessRedstoneBlockEntity wireless){
   if(signal&&settings.contains("WirelessAnalog"))wireless.configure(wireless.getRedstoneChannels(),settings.getBoolean("WirelessAnalog"),WirelessRedstoneData.level(settings.getInt("WirelessOn")),WirelessRedstoneData.level(settings.getInt("WirelessOff")),wireless.port());
   if(values.contains("redstone_channel")||transmit||values.contains("switch_rotation",Tag.TAG_INT))wireless.configureCopiedMetadata(receive(values,wireless.getRedstoneChannels()),transmit,transmit?ChannelList.of(transmission.getIntArray("Channels")):wireless.transmissionChannels(),transmit?transmission.getInt("Range"):wireless.transmissionRange(),transmit?transmission.getBoolean("Local"):wireless.localRedstone(),values.contains("switch_rotation",Tag.TAG_INT)?values.getInt("switch_rotation"):wireless.mountRotation());
  }
 }
 public static CompoundTag capture(BlockEntity tile){
  NativeChannelMember member;CompoundTag settings;int rotation;Boolean active=null;Integer step=null;
  if(tile instanceof NativeBinaryControlBlockEntity control){member=control;settings=control.configuration();rotation=control.mountRotation();if(control instanceof NativeSignalControlBlockEntity signal)step=signal.getStep();else active=control.isLocalOn();}
  else if(tile instanceof NativeWirelessRedstoneBlockEntity wireless){member=wireless;settings=wireless.configuration();rotation=wireless.mountRotation();}
  else throw new IllegalArgumentException("Unsupported copied control");
  var out=new CompoundTag();var channels=member.getRedstoneChannels();if(channels.size()<=1)out.putInt("redstone_channel",channels.first());else out.putIntArray("redstone_channel",channels.toArray());
  var transmission=new CompoundTag();transmission.putIntArray("Channels",member.transmittedChannels().toArray());transmission.putInt("Range",member.transmissionRange());transmission.putBoolean("Local",tile instanceof NativeBinaryControlBlockEntity control?control.localRedstone():((NativeWirelessRedstoneBlockEntity)tile).localRedstone());if(!(tile instanceof NativeWirelessRedstoneBlockEntity wireless)||wireless.transmitter())out.put("Transmission",transmission);
  out.put("signal_settings",settings);out.putInt("switch_rotation",rotation);if(step!=null)out.putInt("active",step);else if(active!=null)out.putBoolean("active",active);return out;
 }
 private NativeCopifierControlEdit(){}
}
