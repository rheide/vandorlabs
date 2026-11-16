package com.vandorlabs.neoforge.ramp;

import java.nio.file.Path;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;

/** Exact previous-release Source snapshots remain quarantined; this is not active-world conversion. */
final class NativeRampSourceRecoveryChecks {
 static void run(ServerLevel level){String input=System.getProperty("vandorlabs.rampSourceSnapshot");if(input==null)throw new IllegalStateException("Missing actual previous-release Ramp fixture");try{
  var saved=NbtIo.readCompressed(Path.of(input),NbtAccounter.create(8*1024*1024));int roots=0,parts=0;
  for(var raw:saved.getList("Cells",Tag.TAG_COMPOUND)){var cell=(CompoundTag)raw;String name=cell.getString("Name");if(!name.equals("vandorlabs:programmable_ramp")&&!name.equals("vandorlabs:controlled_ramp"))continue;var tag=cell.getCompound("Tile").copy();var pos=new BlockPos(cell.getInt("X"),cell.getInt("Y"),cell.getInt("Z"));
   if(name.equals("vandorlabs:programmable_ramp")){int meta=cell.getInt("Meta");var scan=(meta&8)!=0?((meta&1)==0?Direction.UP:Direction.DOWN):Direction.from2DDataValue(meta&3);var face=scan.getAxis().isVertical()?Direction.NORTH:scan;var state=NativeRampBindings.BLOCK.defaultBlockState().setValue(NativeRampBlock.FACING,face).setValue(NativeRampBlock.ACTIVE_FACE,scan).setValue(NativeRampBlock.ACTIVE,(meta&4)!=0).setValue(NativeRampBlock.MODE,NativeRampSettings.read(tag).mode());var root=new NativeRampEntity(NativeRampBindings.ENTITY,pos,state,false);root.setLevel(level);root.loadWithComponents(tag,level.registryAccess());need(root.inert()&&root.recoverySnapshot().equals(tag)&&root.saveWithoutMetadata(level.registryAccess()).equals(tag),"actual Source active root retained exactly, never reinterpreted as settings");int encoded=(scan.getAxis().isVertical()?8|(scan==Direction.DOWN?1:0):scan.get2DDataValue())|(state.getValue(NativeRampBlock.ACTIVE)?4:0);need(encoded==meta&&BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(name),"canonical root registry and Source orientation/activity metadata stable");roots++;}
   else{var part=new NativeRampPartEntity(NativeRampBindings.PART_ENTITY,pos,NativeRampBindings.PART.defaultBlockState());part.setLevel(level);part.loadWithComponents(tag,level.registryAccess());need(!part.ownershipKnown()&&part.publication()==null&&part.saveWithoutMetadata(level.registryAccess()).equals(tag),"actual Source controlled_ramp body/source snapshots retained inert exactly");need(BuiltInRegistries.BLOCK.getKey(NativeRampBindings.PART).toString().equals(name),"hidden generated part registry remains canonical");parts++;}
  }
  need(roots==2&&parts==56,"entire actual previous-release Ramp sample covered");System.out.println("[vandorlabs][ramp-source-recovery] PASS roots="+roots+" parts="+parts+" originalNBTExact/inert/canonicalRegistryMetadata/noActiveWorldConversion");
 }catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
 private static void need(boolean value,String why){if(!value)throw new AssertionError("Source Ramp recovery: "+why);}
}
