package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.ramp.RampLifecycle;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Controller projection of a separately durable ACTIVE journal receipt. It owns no source
 * snapshots or actor inventories and cannot authorize installation by itself. */
final class NativeRampActiveState {
 private static final Set<String> KEYS=Set.of("Schema","Operation","Recipe","Revision","Timeline","Cells","Returning","Belt");
 private static final Set<String> TIME=Set.of("Sequence","Started","LastTick","From","Target","Observed","Duration","Moving");
 final UUID operation;final byte[] recipe;final long revision;final RampLifecycle.Timeline timeline;
 final Set<BlockPos> cells;final boolean returning;final NativeRampPartData.Belt belt;
 NativeRampActiveState(UUID operation,byte[] recipe,long revision,RampLifecycle.Timeline timeline,Collection<BlockPos> cells,boolean returning,NativeRampPartData.Belt belt){
  this.operation=Objects.requireNonNull(operation);if(recipe.length!=32||revision<0||cells.isEmpty()||cells.size()>8192)throw new IllegalArgumentException("Ramp ACTIVE receipt bounds");this.recipe=recipe.clone();this.revision=revision;this.timeline=Objects.requireNonNull(timeline);var copy=new LinkedHashSet<BlockPos>();for(var pos:cells)if(!copy.add(pos.immutable()))throw new IllegalArgumentException("Duplicate ACTIVE coordinate");this.cells=Set.copyOf(copy);this.returning=returning;this.belt=Objects.requireNonNull(belt);
 }
 CompoundTag write(){
  var tag=new CompoundTag();tag.putInt("Schema",1);tag.putUUID("Operation",operation);tag.putByteArray("Recipe",recipe.clone());tag.putLong("Revision",revision);tag.putBoolean("Returning",returning);var band=new CompoundTag();band.putInt("Mode",belt.mode());band.putInt("Speed",belt.speed());band.putDouble("Distance",belt.distance());band.putLong("Tick",belt.tick());band.putBoolean("Running",belt.running());band.putDouble("Observed",belt.observed());tag.put("Belt",band);
  var motion=new CompoundTag();motion.putLong("Sequence",timeline.sequence);motion.putLong("Started",timeline.started);motion.putLong("LastTick",timeline.lastTick);motion.putDouble("From",timeline.from);motion.putDouble("Target",timeline.target);motion.putDouble("Observed",timeline.observed);motion.putInt("Duration",timeline.duration);motion.putBoolean("Moving",timeline.moving);tag.put("Timeline",motion);
  var positions=new ListTag();for(var pos:cells.stream().sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).toList())positions.add(new IntArrayTag(new int[]{pos.getX(),pos.getY(),pos.getZ()}));tag.put("Cells",positions);return tag;
 }
 static NativeRampActiveState read(CompoundTag tag,BlockPos root){
  if(!tag.getAllKeys().equals(KEYS)||!tag.contains("Schema",Tag.TAG_INT)||tag.getInt("Schema")!=1||!tag.hasUUID("Operation")||!tag.contains("Recipe",Tag.TAG_BYTE_ARRAY)||!tag.contains("Revision",Tag.TAG_LONG)||!tag.contains("Timeline",Tag.TAG_COMPOUND)||!tag.contains("Cells",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown ACTIVE receipt schema");
  var recipe=tag.getByteArray("Recipe");if(recipe.length!=32)throw new IllegalArgumentException("ACTIVE recipe identity");var time=tag.getCompound("Timeline");if(!time.getAllKeys().equals(TIME))throw new IllegalArgumentException("Unknown ACTIVE timeline field");for(String key:new String[]{"Sequence","Started","LastTick"})if(!time.contains(key,Tag.TAG_LONG))throw new IllegalArgumentException("ACTIVE timeline integer type");for(String key:new String[]{"From","Target","Observed"})if(!time.contains(key,Tag.TAG_DOUBLE))throw new IllegalArgumentException("ACTIVE timeline pose type");if(!time.contains("Duration",Tag.TAG_INT))throw new IllegalArgumentException("ACTIVE duration type");
  var timeline=new RampLifecycle.Timeline(time.getLong("Sequence"),time.getLong("Started"),time.getLong("LastTick"),time.getDouble("From"),time.getDouble("Target"),time.getDouble("Observed"),time.getInt("Duration"),bool(time,"Moving"));
  var positions=tag.getList("Cells",Tag.TAG_INT_ARRAY);if(positions.isEmpty()||positions.size()>8192||positions.size()!=((ListTag)tag.get("Cells")).size())throw new IllegalArgumentException("ACTIVE coordinate count/type");var cells=new ArrayList<BlockPos>();for(var entry:positions){int[] p=((IntArrayTag)entry).getAsIntArray();if(p.length!=3||Math.abs((long)p[0]-root.getX())>64||Math.abs((long)p[1]-root.getY())>64||Math.abs((long)p[2]-root.getZ())>64)throw new IllegalArgumentException("ACTIVE coordinate bounds");cells.add(new BlockPos(p[0],p[1],p[2]));}
  if(!tag.contains("Belt",Tag.TAG_COMPOUND))throw new IllegalArgumentException("ACTIVE belt type");var band=tag.getCompound("Belt");if(!band.getAllKeys().equals(Set.of("Mode","Speed","Distance","Tick","Running","Observed"))||!band.contains("Mode",Tag.TAG_INT)||!band.contains("Speed",Tag.TAG_INT)||!band.contains("Distance",Tag.TAG_DOUBLE)||!band.contains("Tick",Tag.TAG_LONG)||!band.contains("Observed",Tag.TAG_DOUBLE))throw new IllegalArgumentException("ACTIVE belt schema");var belt=new NativeRampPartData.Belt(band.getInt("Mode"),band.getInt("Speed"),band.getDouble("Distance"),band.getLong("Tick"),bool(band,"Running"),band.getDouble("Observed"));
  return new NativeRampActiveState(tag.getUUID("Operation"),recipe,tag.getLong("Revision"),timeline,cells,bool(tag,"Returning"),belt);
 }
 static byte[] recipe(CompoundTag recipe){
  if(recipe.sizeInBytes()>2*1024*1024)throw new IllegalArgumentException("ACTIVE recipe byte limit");
  try{var bytes=new java.io.ByteArrayOutputStream();try(var output=new java.io.DataOutputStream(bytes)){NbtIo.write(canonical(recipe),output);}return java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());}
  catch(java.io.IOException|java.security.NoSuchAlgorithmException failure){throw new IllegalStateException("ACTIVE recipe identity unavailable",failure);}
 }
 private static CompoundTag canonical(CompoundTag tag){var result=new CompoundTag();for(var key:new java.util.TreeSet<>(tag.getAllKeys()))result.put(key,canonicalValue(tag.get(key)));return result;}
 private static Tag canonicalValue(Tag tag){if(tag instanceof CompoundTag compound)return canonical(compound);if(tag instanceof ListTag list){var copy=new ListTag();for(var value:list)copy.add(canonicalValue(value));return copy;}return tag.copy();}
 private static boolean bool(CompoundTag tag,String key){if(!tag.contains(key,Tag.TAG_BYTE)||(tag.getByte(key)!=0&&tag.getByte(key)!=1))throw new IllegalArgumentException("ACTIVE boolean type");return tag.getBoolean(key);}
}
