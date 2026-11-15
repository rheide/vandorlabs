package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Exact native root identity committed with the initial private journal, independently
 * of the mutable automation authorization ledger. Never exported through items or S2C. */
final class NativeRampRootIdentity {
 static final String KEY="RootIdentity";
 private static final Set<String> KEYS=Set.of("Schema","Owner","Type","State");
 final UUID owner;final ResourceLocation type;final BlockState state;
 NativeRampRootIdentity(UUID owner,ResourceLocation type,BlockState state){this.owner=Objects.requireNonNull(owner);this.type=Objects.requireNonNull(type);this.state=NativeRampRuntime.normalized(state);if(!(state.getBlock() instanceof NativeRampBlock)||!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(type)||((NativeRampBlock)state.getBlock()).entityType()!=BuiltInRegistries.BLOCK_ENTITY_TYPE.get(type))throw new IllegalArgumentException("Unknown Ramp root identity family");}
 static NativeRampRootIdentity capture(NativeRampEntity root){return new NativeRampRootIdentity(root.ensureOwnerIdentity(),BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(root.getType()),root.getBlockState());}
 CompoundTag write(){var tag=new CompoundTag();tag.putInt("Schema",1);tag.putUUID("Owner",owner);tag.putString("Type",type.toString());tag.put("State",NbtUtils.writeBlockState(state));return tag;}
 static NativeRampRootIdentity read(CompoundTag tag,HolderLookup.Provider registries){
  if(!tag.getAllKeys().equals(KEYS)||!tag.contains("Schema",Tag.TAG_INT)||tag.getInt("Schema")!=1||!tag.hasUUID("Owner")||!tag.contains("Type",Tag.TAG_STRING)||!tag.contains("State",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Unknown Ramp root identity envelope");var type=ResourceLocation.tryParse(tag.getString("Type"));if(type==null)throw new IllegalArgumentException("Malformed Ramp root type");var state=com.vandorlabs.neoforge.transfer.NativeRawCell.decodePalette(registries,tag.getCompound("State"),Map.of());var value=new NativeRampRootIdentity(tag.getUUID("Owner"),type,state);if(!value.write().equals(tag))throw new IllegalArgumentException("Noncanonical Ramp root identity");return value;
 }
 void require(NativeRampEntity root){if(!owner.equals(root.ownerIdentity())||!type.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(root.getType()))||state!=NativeRampRuntime.normalized(root.getBlockState()))throw new IllegalArgumentException("Ramp root identity changed");}
}
