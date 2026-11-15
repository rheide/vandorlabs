package com.vandorlabs.neoforge.ramp;

import java.util.*;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Small saved controller projection. Durable source/resource ownership belongs exclusively
 * to the operation journal. This projection cannot authorize adoption on its own. */
final class NativeRampRuntime {
 static final String KEY="NativeRampRuntime",OWNER="NativeRampOwner";
 private static final Set<String> KEYS=Set.of("Schema","Owner","RootType","RootState","Frame","Requested","Signal","SignalKnown","Held","Error","Status");
 final UUID owner;final ResourceLocation type;final BlockState rootState;final NativeRampActiveState frame;final NativeRampSettings queued;final double requested,signal;final boolean signalKnown,held,error;final String status;
 NativeRampRuntime(UUID owner,ResourceLocation type,BlockState rootState,NativeRampActiveState frame,NativeRampSettings queued,double requested,double signal,boolean signalKnown,boolean held,boolean error,String status){
  this.owner=Objects.requireNonNull(owner);this.type=Objects.requireNonNull(type);this.rootState=normalized(Objects.requireNonNull(rootState));this.frame=Objects.requireNonNull(frame);this.queued=queued;this.requested=requested;this.signal=signal;this.signalKnown=signalKnown;this.held=held;this.error=error;this.status=Objects.requireNonNull(status);if(!Double.isFinite(requested)||requested<0||requested>1||!Double.isFinite(signal)||signal<0||signal>1||status.length()>256)throw new IllegalArgumentException("Ramp runtime control bounds");
  if(!(rootState.getBlock() instanceof NativeRampBlock)||!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(type))throw new IllegalArgumentException("Ramp runtime root family");
 }
 private static Set<String> withQueued(){var keys=new HashSet<>(KEYS);keys.add("Queued");return keys;}
 static boolean itemAuthority(CompoundTag tag){return tag.contains(OWNER)||tag.contains(KEY)||tag.getCompound("NeoForgeData").contains("VandorRampJournal");}
 static BlockState normalized(BlockState state){return state.hasProperty(NativeRampBlock.ACTIVE)?state.setValue(NativeRampBlock.ACTIVE,false):state;}
 CompoundTag write(){var tag=new CompoundTag();tag.putInt("Schema",1);tag.putUUID("Owner",owner);tag.putString("RootType",type.toString());tag.put("RootState",NbtUtils.writeBlockState(rootState));tag.put("Frame",frame.write());tag.putDouble("Requested",requested);tag.putDouble("Signal",signal);tag.putBoolean("SignalKnown",signalKnown);tag.putBoolean("Held",held);tag.putBoolean("Error",error);tag.putString("Status",status);if(queued!=null){var settings=new CompoundTag();queued.writeChoices(settings);tag.put("Queued",settings);}return tag;}
 static NativeRampRuntime read(CompoundTag tag,BlockPos root,HolderLookup.Provider registries){
  if(tag.sizeInBytes()>512*1024||!tag.getAllKeys().equals(tag.contains("Queued")?withQueued():KEYS)||!tag.contains("Schema",Tag.TAG_INT)||tag.getInt("Schema")!=1||!tag.hasUUID("Owner")||!tag.contains("RootType",Tag.TAG_STRING)||!tag.contains("RootState",Tag.TAG_COMPOUND)||!tag.contains("Frame",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Unknown Ramp runtime projection schema");
  var type=ResourceLocation.tryParse(tag.getString("RootType"));if(type==null||!type.toString().equals(tag.getString("RootType")))throw new IllegalArgumentException("Ramp runtime root type");
  var state=NativeRawCell.decodePalette(registries,tag.getCompound("RootState"),Map.of());NativeRampSettings queued=null;if(tag.contains("Queued")){if(!tag.contains("Queued",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Ramp queued settings type");queued=NativeRampSettings.read(tag.getCompound("Queued"));var canonical=new CompoundTag();queued.writeChoices(canonical);if(!canonical.equals(tag.getCompound("Queued")))throw new IllegalArgumentException("Unknown queued Ramp settings");}var value=new NativeRampRuntime(tag.getUUID("Owner"),type,state,NativeRampActiveState.read(tag.getCompound("Frame"),root),queued,scalar(tag,"Requested"),scalar(tag,"Signal"),flag(tag,"SignalKnown"),flag(tag,"Held"),flag(tag,"Error"),text(tag,"Status"));if(!value.write().equals(tag))throw new IllegalArgumentException("Noncanonical Ramp runtime projection");return value;
 }
 private static double scalar(CompoundTag tag,String key){if(!tag.contains(key,Tag.TAG_DOUBLE))throw new IllegalArgumentException("Ramp runtime control scalar type");return tag.getDouble(key);}
 private static boolean flag(CompoundTag tag,String key){if(!tag.contains(key,Tag.TAG_BYTE)||(tag.getByte(key)!=0&&tag.getByte(key)!=1))throw new IllegalArgumentException("Ramp runtime control flag type");return tag.getBoolean(key);}
 private static String text(CompoundTag tag,String key){if(!tag.contains(key,Tag.TAG_STRING))throw new IllegalArgumentException("Ramp runtime control text type");return tag.getString(key);}
 void requireRoot(NativeRampEntity entity,UUID expectedOwner){
  if(!owner.equals(expectedOwner)||!type.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()))||normalized(entity.getBlockState())!=rootState)throw new IllegalArgumentException("Saved Ramp root identity/state differs from ACTIVE receipt");
 }
}
