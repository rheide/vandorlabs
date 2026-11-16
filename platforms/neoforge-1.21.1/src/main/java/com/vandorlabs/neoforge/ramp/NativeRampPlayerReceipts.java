package com.vandorlabs.neoforge.ramp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.*;

/** Private WAL player preimages. They are comparison evidence, never a player/inventory restore image. */
final class NativeRampPlayerReceipts {
 static final String KEY="InitialPlayerRecords";
 static final int MAX_PLAYERS=8;
 private static final Set<String> KEYS=Set.of("Id","Present","Before");
 static ListTag capture(Path directory,NativeRampActorIntent intent)throws IOException {
  var result=new ListTag();
  for(var actor:intent.participants())if(actor.type().equals("minecraft:player")){
   if(result.size()>=MAX_PLAYERS)throw new IOException("Ramp player handoff count limit");
   var saved=NativeRampPlayerStorage.read(directory,actor.id());
   if(saved.isPresent())try{NativeRampPlayerRecord.identity(saved.get(),actor.id(),intent.dimension());}catch(IllegalArgumentException unknown){throw new IOException("Player saved UUID/dimension differs from operation",unknown);}
   var row=new CompoundTag();row.putUUID("Id",actor.id());row.putBoolean("Present",saved.isPresent());saved.ifPresent(tag->row.put("Before",tag));result.add(row);
  }
  return result;
 }
 static Map<UUID,Optional<CompoundTag>> read(CompoundTag record,NativeRampActorIntent intent){
  var expected=new HashMap<UUID,NativeRampActorIntent.Participant>();for(var actor:intent.participants())if(actor.type().equals("minecraft:player"))expected.put(actor.id(),actor);
  if(expected.size()>MAX_PLAYERS)throw new IllegalArgumentException("Ramp player receipt count");
  if(!record.contains(KEY)){if(!expected.isEmpty())throw new IllegalArgumentException("Ramp initial player receipt missing");return Map.of();}
  if(!record.contains(KEY,Tag.TAG_LIST))throw new IllegalArgumentException("Ramp player receipt list type");
  var list=record.getList(KEY,Tag.TAG_COMPOUND);if(list.size()!=expected.size()||list.size()!=((ListTag)record.get(KEY)).size())throw new IllegalArgumentException("Ramp player receipt roster");
  var result=new HashMap<UUID,Optional<CompoundTag>>();
  for(var element:list){var row=(CompoundTag)element;if(!row.hasUUID("Id")||!row.contains("Present",Tag.TAG_BYTE)||(row.getByte("Present")!=0&&row.getByte("Present")!=1))throw new IllegalArgumentException("Ramp player receipt identity/type");boolean present=row.getBoolean("Present");if(!row.getAllKeys().equals(present?KEYS:Set.of("Id","Present"))||present&&!row.contains("Before",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Ramp player receipt fields");var id=row.getUUID("Id");if(!expected.containsKey(id)||result.containsKey(id))throw new IllegalArgumentException("Ramp unrelated/duplicate player receipt");Optional<CompoundTag> saved=present?Optional.of(row.getCompound("Before")):Optional.empty();if(present){if(saved.get().sizeInBytes()>NativeRampPlayerStorage.BYTES)throw new IllegalArgumentException("Ramp player receipt bytes");NativeRampPlayerRecord.identity(saved.get(),id,intent.dimension());}result.put(id,saved);}
  return Map.copyOf(result);
 }
 static void repair(Path directory,NativeRampActorIntent intent,Map<UUID,Optional<CompoundTag>> receipts)throws IOException {repair(directory,intent,receipts,false);}
 static void repair(Path directory,NativeRampActorIntent intent,Map<UUID,Optional<CompoundTag>> receipts,boolean returning)throws IOException {
  // Classify every player before writing the first record. Unknown physical state never authorizes a partial repair.
  var changed=new LinkedHashMap<UUID,CompoundTag>();var before=new HashMap<UUID,Optional<CompoundTag>>();
  for(var actor:intent.participants())if(actor.type().equals("minecraft:player")){
   var current=NativeRampPlayerStorage.read(directory,actor.id());var classification=returning?NativeRampPlayerRecord.classifyReturn(current,receipts.get(actor.id()),actor,intent.dimension()):NativeRampPlayerRecord.classify(current,receipts.get(actor.id()),actor,intent.dimension());
   if(classification.state()==NativeRampPlayerRecord.State.REFUSED)throw new IOException("Ramp player record retained: "+classification.reason());
   if(classification.state()==NativeRampPlayerRecord.State.TRANSFERRED){before.put(actor.id(),current);changed.put(actor.id(),NativeRampPlayerRecord.returned(current.orElseThrow(),receipts.get(actor.id()),actor,intent.dimension(),returning));}
  }
  for(var entry:changed.entrySet())NativeRampPlayerStorage.replace(directory,entry.getKey(),before.get(entry.getKey()),entry.getValue());
 }
 private NativeRampPlayerReceipts(){}
}
