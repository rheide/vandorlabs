package com.vandorlabs.neoforge.gear;
import com.vandorlabs.gear.core.ArmHinge;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
/** Optional own fields never normalize unknown journal data; the packet budget remains unchanged. */
public final class NativeGearDoubleSettingsChecks {
 private static int checks;
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Native Double settings: "+why);}
 public static void main(String[] args){int cases=0;
  for(var mode:ArmHinge.Mode.values())for(int fold=0;fold<4;fold++)for(int kink=-1;kink<4;kink++)for(boolean populated:new boolean[]{false,true}){
   var value=new NativeGearSettings(2,fold,populated?ChannelList.of(4,9):ChannelList.EMPTY,true,true,true,mode,true,8,true,3,-30,120,2,1,kink);
   need(NativeGearSettings.readChoices(value.editableChoices()).equals(value),"editable implicit/explicit kink");
   var saved=new CompoundTag();var opaque=new CompoundTag();opaque.putString("owner","untouched");saved.put("ForgeCaps",opaque.copy());saved.putString("foreign","unknown");value.writeSaved(saved);
   need(NativeGearSettings.readSaved(saved).equals(value)&&saved.getCompound("ForgeCaps").equals(opaque)&&saved.getString("foreign").equals("unknown"),"saved settings/opaque fields");
   need(saved.contains("ArmHingeMode")==(mode==ArmHinge.Mode.DOUBLE)&&saved.contains("ArmKinkDirection")==(kink>=0),"necessary optional fields only");
   var packet=new NativeGearSettingsPayload(7,new BlockPos(-17,80,19),value,true,91);var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
   try{NativeGearSettingsPayload.CODEC.encode(bytes,packet);need(bytes.readableBytes()==(populated?52:49),"unchanged packet size");need(NativeGearSettingsPayload.CODEC.decode(bytes).equals(packet)&&!bytes.isReadable(),"all flags roundtrip");}finally{bytes.release();}cases++;
  }
  var old=new CompoundTag();old.putBoolean("ArmMiddleHinge",true);need(NativeGearSettings.readSaved(old).hingeMode()==ArmHinge.Mode.LEGACY_CENTER,"saved old stroke preserved");
  var choice=new CompoundTag();choice.putBoolean("MiddleHinge",true);need(NativeGearSettings.readChoices(choice).hingeMode()==ArmHinge.Mode.DOUBLE,"new application selects Double");
  var original=NativeGearSettings.readSaved(old).choices();var prior=original.copy();prior.remove("HingeMode");prior.remove("KinkDirection");need(NativeGearSettings.sameRetainedChoices(original,prior),"known absent fields equivalent");prior.putString("unknown","new");need(!NativeGearSettings.sameRetainedChoices(original,prior),"unknown changes refused");
  for(int flags:new int[]{128,256,768,2048,65535}){var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{NativeGearSettingsPayload.CODEC.encode(bytes,new NativeGearSettingsPayload(1,BlockPos.ZERO,NativeGearSettings.defaults(),true));bytes.setShort(15,flags);boolean refused=false;try{NativeGearSettingsPayload.CODEC.decode(bytes);}catch(IllegalArgumentException invalid){refused=true;}need(refused,"invalid flags "+flags);}finally{bytes.release();}}
  for(int raw:new int[]{-1,3,99}){var invalid=old.copy();invalid.putInt("ArmHingeMode",raw);boolean refused=false;try{NativeGearSettings.readSaved(invalid);}catch(IllegalArgumentException expected){refused=true;}need(refused,"unknown saved mode");}
  var malformed=old.copy();malformed.putString("ArmKinkDirection","UP");boolean refused=false;try{NativeGearSettings.readSaved(malformed);}catch(IllegalArgumentException expected){refused=true;}need(refused,"non-planar kink refused");
  System.out.println("Native Double settings PASS cases="+cases+" checks="+checks+" fixedHeader48/default49bytes/oldSaved/oldChoices/implicitKink/exactJournal/typedInvalidFlags");
 }
 private NativeGearDoubleSettingsChecks(){}
}
