package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.persistence.JournalFiles;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Worker-only force/compare of vanilla's current physical participant record. Never writes
 * an old player snapshot or an inventory. The server must retain its exact operation fence. */
final class NativeRampPhysicalPlayerCheckpoint {
 static void force(Path directory,NativeRampActorIntent intent)throws IOException {
  var records=new LinkedHashMap<UUID,net.minecraft.nbt.CompoundTag>();
  for(var actor:intent.participants())if(actor.type().equals("minecraft:player")){
   if(records.size()>=NativeRampPlayerReceipts.MAX_PLAYERS)throw new IOException("Ramp participant checkpoint count");
   var current=NativeRampPlayerStorage.read(directory,actor.id());
   var classified=NativeRampPlayerRecord.classify(current,Optional.empty(),actor,intent.dimension());
   if(classified.state()!=NativeRampPlayerRecord.State.TRANSFERRED&&!(actor.from().equals(actor.to())&&classified.state()==NativeRampPlayerRecord.State.ORIGINAL))throw new IOException("Participant physical record does not match committed Ramp endpoint: "+classified.reason());
   records.put(actor.id(),current.orElseThrow());
  }
  for(var entry:records.entrySet()){
   var path=directory.resolve(entry.getKey()+".dat");
   try(var file=FileChannel.open(path,StandardOpenOption.WRITE)){file.force(true);}
   if(!NativeRampPlayerStorage.read(directory,entry.getKey()).equals(Optional.of(entry.getValue())))throw new IOException("Participant physical record changed during checkpoint");
  }
  if(!records.isEmpty())JournalFiles.syncDirectory(directory);
 }
 private NativeRampPhysicalPlayerCheckpoint(){}
}
