package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.persistence.JournalFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;

/** Worker-only bounded player-file access. Callers supply detached paths and immutable UUIDs;
 * no server, level, player or capability object crosses the I/O boundary. */
final class NativeRampPlayerStorage {
 static final int BYTES=512*1024;
 private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),task->{var thread=new Thread(task,"Vandor Ramp player repair");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
 static CompletableFuture<Void> repair(Path directory,NativeRampActorIntent intent,Map<UUID,Optional<CompoundTag>> receipts){return repair(directory,intent,receipts,false);}
 static CompletableFuture<Void> repair(Path directory,NativeRampActorIntent intent,Map<UUID,Optional<CompoundTag>> receipts,boolean returning){
  var result=new CompletableFuture<Void>();try{IO.execute(()->{try{NativeRampPlayerReceipts.repair(directory,intent,receipts,returning);result.complete(null);}catch(Throwable failure){result.completeExceptionally(failure);}});}catch(RejectedExecutionException busy){result.completeExceptionally(new IOException("Ramp player repair queue is busy",busy));}return result;
 }
 /** Null is bounded executor backpressure, never a rejected ownership action. */
 static CompletableFuture<Void> forceParticipants(Path directory,NativeRampActorIntent intent){
  var result=new CompletableFuture<Void>();try{IO.execute(()->{try{NativeRampPhysicalPlayerCheckpoint.force(directory,intent);result.complete(null);}catch(Throwable failure){result.completeExceptionally(failure);}});}catch(RejectedExecutionException busy){return null;}return result;
 }
 static Optional<CompoundTag> read(Path directory,UUID id)throws IOException {
  var file=directory.resolve(id+".dat");
  try{var attributes=Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(!attributes.isRegularFile()||attributes.size()>BYTES)throw new IOException("Unsupported player record retained");}
  catch(NoSuchFileException absent){return Optional.empty();}
  var tag=NbtIo.readCompressed(file,NbtAccounter.create(BYTES));if(tag.sizeInBytes()>BYTES)throw new IOException("Player record byte limit");return Optional.of(tag);
 }
 static void replace(Path directory,UUID id,Optional<CompoundTag> expected,CompoundTag replacement)throws IOException {
  if(expected.isEmpty()||!read(directory,id).equals(expected))throw new IOException("Player record changed before repair");
  if(replacement.sizeInBytes()>BYTES)throw new IOException("Player repair byte limit");
  var file=directory.resolve(id+".dat");var pending=directory.resolve(id+".vandor-ramp.pending");
  try{
   try(var output=new FileOutputStream(pending.toFile())){var shield=new FilterOutputStream(output){long bytes;@Override public void write(int value)throws IOException{if(++bytes>BYTES)throw new IOException("Player compressed byte limit");out.write(value);}@Override public void write(byte[] bytes,int offset,int length)throws IOException{this.bytes+=length;if(this.bytes>BYTES)throw new IOException("Player compressed byte limit");out.write(bytes,offset,length);}@Override public void close()throws IOException{flush();}};NbtIo.writeCompressed(replacement,shield);output.getFD().sync();}
   if(!NbtIo.readCompressed(pending,NbtAccounter.create(BYTES)).equals(replacement))throw new IOException("Player repair round trip differs");
   if(!read(directory,id).equals(expected))throw new IOException("Player record changed during repair");
   Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);JournalFiles.syncDirectory(directory);
  }finally{Files.deleteIfExists(pending);}
 }
 private NativeRampPlayerStorage(){}
}
