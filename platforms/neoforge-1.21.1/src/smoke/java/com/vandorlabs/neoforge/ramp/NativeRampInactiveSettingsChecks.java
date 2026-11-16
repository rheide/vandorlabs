package com.vandorlabs.neoforge.ramp;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.Half;

/** Analog deployment is an ordinary saved choice, never an active-ownership sentinel. */
final class NativeRampInactiveSettingsChecks {
 static void run(ServerLevel level){int checks=0;for(var scan:Direction.values())for(int housing=0;housing<3;housing++)for(boolean analog:new boolean[]{false,true}){
  var state=housing==0?NativeRampBindings.BLOCK.defaultBlockState():NativeRampBindings.SLAB.defaultBlockState().setValue(NativeRampSlabBlock.HALF,housing==2?Half.TOP:Half.BOTTOM);
  state=state.setValue(NativeRampBlock.FACING,scan.getAxis().isHorizontal()?scan:Direction.NORTH).setValue(NativeRampBlock.ACTIVE_FACE,scan);
  var settings=NativeRampSettings.defaults().withAnalogDeployment(analog);var tag=new CompoundTag();settings.writeChoices(tag);
  var root=new NativeRampEntity(NativeRampBindings.ENTITY,BlockPos.ZERO,state,housing!=0);root.setLevel(level);root.loadWithComponents(tag,level.registryAccess());need(!root.inert()&&root.settings().equals(settings),"both analog choices load as ordinary inactive settings");
  var saved=root.saveWithoutMetadata(level.registryAccess());var restored=new NativeRampEntity(NativeRampBindings.ENTITY,BlockPos.ZERO,state,housing!=0);restored.setLevel(level);restored.loadWithComponents(saved,level.registryAccess());need(!restored.inert()&&restored.settings().equals(settings),"saved inactive settings round trip across all scan faces and slab halves");checks+=2;
  for(int malformed=0;malformed<4;malformed++){var bad=saved.copy();if(malformed==0)bad.putInt("RampAnalogDeployment",1);else if(malformed==1)bad.putByte("RampAnalogDeployment",(byte)2);else if(malformed==2)bad.putString("RampAnalogDeployment","true");else bad.putBoolean("Moving",true);restored.loadWithComponents(bad,level.registryAccess());need(restored.inert()&&restored.recoverySnapshot().equals(bad)&&restored.saveWithoutMetadata(level.registryAccess()).equals(bad),"malformed flags and active Source ownership remain quarantined exactly");checks++;}
 }System.out.println("[vandorlabs][ramp-inactive-settings] PASS checks="+checks+" analogBothValues/sixFaces/slabHalves/roundTrip/unknownOwnershipExact");}
 private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
 private NativeRampInactiveSettingsChecks(){}
}
