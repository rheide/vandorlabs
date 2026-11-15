package com.vandorlabs.neoforge.gear;
import com.vandorlabs.gear.core.ArmLandingAngles;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
public final class NativeLandingAngleCodecChecks {
 private static int checks;private static void need(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 @SuppressWarnings("deprecation") public static void main(String[] args){
  var raw=new CompoundTag();raw.putDouble("ArmStartAngle",45);raw.putDouble("ArmActiveAngle",135);raw.putString("GearChannels","");raw.putString("UnknownOpaque","retained");var original=raw.copy();
  var legacy=NativeGearSettings.readSaved(raw);need(raw.equals(original),"input preimage immutable");need(legacy.landingAnglesVersion()==-1,"absent legacy version");need(legacy.angles().start(true)==0&&legacy.angles().end(true)==90,"effective legacy0/90");need(legacy.startAngle()==45&&legacy.activeAngle()==135,"raw legacy45/135 retained");
  var saved=raw.copy();legacy.writeSaved(saved);need(!saved.contains(ArmLandingAngles.SAVED)&&saved.getDouble("ArmStartAngle")==45&&saved.getDouble("ArmActiveAngle")==135&&saved.getString("UnknownOpaque").equals("retained"),"saved absence/raw/unknown retained");
  var copy=legacy.copied(true);need(copy.landingAnglesVersion()==1&&copy.startAngle()==0&&copy.activeAngle()==90,"semantic copied landing effective0/90");
  var edit=legacy.withAngles(legacy.angles().edit(true,false,60));need(edit.startAngle()==0&&edit.activeAngle()==60&&edit.landingAnglesVersion()==1,"first edit adopts both effective endpoints");
  for(int version=-1;version<=1;version++){
   var value=legacy.withAngles(new ArmLandingAngles(version,45,135));var tag=new CompoundTag();value.writeSaved(tag);need(NativeGearSettings.readSaved(tag).equals(value),"saved version roundtrip");need(NativeGearSettings.readChoices(value.editableChoices()).equals(value),"choices version roundtrip");
   var packet=new NativeGearSettingsPayload(3,new BlockPos(8,64,-2),value,true,7);var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
   try{NativeGearSettingsPayload.CODEC.encode(bytes,packet);need(bytes.readableBytes()==49,"unchanged empty-channel canonical packet49bytes actual="+bytes.readableBytes());need(NativeGearSettingsPayload.CODEC.decode(bytes).equals(packet)&&!bytes.isReadable(),"packet roundtrip all presence profiles");}finally{bytes.release();}
  }
  for(int invalid:new int[]{-2,-1,2,99}){var bad=raw.copy();bad.putInt(ArmLandingAngles.SAVED,invalid);try{NativeGearSettings.readSaved(bad);throw new AssertionError("bad schema accepted");}catch(IllegalArgumentException expected){checks++;}need(bad.getInt(ArmLandingAngles.SAVED)==invalid,"bad input not normalized");}
  var bad=raw.copy();bad.putString(ArmLandingAngles.SAVED,"1");try{NativeGearSettings.readSaved(bad);throw new AssertionError("bad schema type accepted");}catch(IllegalArgumentException expected){checks++;}
  var control=legacy.editableChoices();control.putInt("JoinRange",12);var nonAngle=NativeGearSettings.readChoices(control).retainLegacyAngles(legacy,true);need(nonAngle.startAngle()==45&&nonAngle.activeAngle()==135&&nonAngle.landingAnglesVersion()==-1,"non-angle edit keeps legacy raw fields");
  var genericCopy=NativeGearCopiedSettings.capture(legacy);need(genericCopy.getCompound("modular_arm_settings").getInt("LandingAnglesVersion")==1&&genericCopy.getCompound("modular_arm_settings").getDouble("StartAngle")==45,"new generic capture exports intentional raw angle/version1");
  var current=legacy.withAngles(new ArmLandingAngles(1,0,60));var oldCopy=genericCopy.copy();oldCopy.getCompound("modular_arm_settings").remove("LandingAnglesVersion");var prepared=NativeGearCopiedSettings.prepare(current,oldCopy).settings();need(prepared.landingAnglesVersion()==-1,"old clipboard cannot inherit target opt-in");var retained=prepared.retainLegacyAngles(current,true);need(retained.startAngle()==current.startAngle()&&retained.activeAngle()==current.activeAngle()&&retained.landingAnglesVersion()==current.landingAnglesVersion(),"old clipboard ignores formerly ignored landing angle remnants");
  for(int flags:new int[]{4096,8192,16384,32768}){var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{NativeGearSettingsPayload.CODEC.encode(bytes,new NativeGearSettingsPayload(3,BlockPos.ZERO,legacy,true));bytes.setShort(15,flags);try{NativeGearSettingsPayload.CODEC.decode(bytes);throw new AssertionError("unknown/incomplete version bits accepted");}catch(IllegalArgumentException expected){checks++;}}finally{bytes.release();}}
  for(int length:new int[]{0,1,47,48+com.vandorlabs.redstone.ChannelList.MAX_TEXT_LENGTH*3+6}){var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{bytes.writeZero(length);try{NativeGearSettingsPayload.CODEC.decode(bytes);throw new AssertionError("invalid packet length accepted");}catch(IllegalArgumentException expected){checks++;}}finally{bytes.release();}}
  System.out.println("native-landing-angle-codec PASS checks="+checks+" packet49(previous49) bytes absent/legacy/configured");
 }
}
