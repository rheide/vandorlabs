package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.mixin.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.*;

/** Inactive startup-only raw storage bridge. Recovery policy owns actor projection and cross-chunk intent. */
final class NativeRampActorStorage {
 static final int MAX_RAW_BYTES=4*1024*1024,MAX_PREPARED_BYTES=32*1024*1024,MAX_PENDING_READS=8,MAX_CHUNKS=512;
 private static final Map<ServerLevel,Permit> LIVE=new IdentityHashMap<>();
 enum Status {PENDING,REFUSED,SUBMITTED}
 record Commit(Status status,CompletableFuture<Void> completion,String reason){}
 static Permit acquire(ServerLevel level){
  requireStartup(level);if(LIVE.containsKey(level))throw new IllegalStateException("Ramp actor storage already scoped");
  var manager=(NativeRampEntityManagerAccess)(Object)((NativeRampEntityLevelAccess)(Object)level).vandorlabs$rampActorManager();
  if(!(manager.vandorlabs$rampActorStorage() instanceof EntityStorage storage))throw new IllegalStateException("Unknown entity storage adapter");
  var permit=new Permit(level,manager,(NativeRampEntityStorageAccess)(Object)storage);LIVE.put(level,permit);return permit;
 }
 private static void requireStartup(ServerLevel level){
  NativeRampRiders.requireThread(level);if(!NativeRampRecoveryAdmission.active(level)||level.getServer().getTickCount()!=0||!level.getServer().getPlayerList().getPlayers().isEmpty())throw new IllegalStateException("Ramp actor storage requires isolated startup admission");
 }
 static final class Permit implements AutoCloseable {
  private final ServerLevel level;private final NativeRampEntityManagerAccess manager;private final NativeRampEntityStorageAccess entityStorage;private final SimpleRegionStorage regions;
  private final Set<ChunkPos> touched=new LinkedHashSet<>();private final Map<ChunkPos,Long> revisions=new HashMap<>();private final Set<Prepared> prepared=Collections.newSetFromMap(new IdentityHashMap<>());private final List<CompletableFuture<Void>> writes=new ArrayList<>();private final AtomicInteger reads=new AtomicInteger();
  private int stagedBytes;private long writeSequence;private final AtomicLong durableSequence=new AtomicLong();private boolean closed;
  private Permit(ServerLevel level,NativeRampEntityManagerAccess manager,NativeRampEntityStorageAccess storage){this.level=level;this.manager=manager;entityStorage=storage;regions=storage.vandorlabs$rampActorRegions();}
  private void active(){requireStartup(level);if(closed||LIVE.get(level)!=this)throw new IllegalStateException("Ramp actor storage scope expired");}
  private void fresh(ChunkPos chunk){
   active();long key=chunk.toLong();Object status=manager.vandorlabs$rampActorLoads().get(key);
   if(status!=null&&(!(status instanceof Enum<?> value)||!value.name().equals("FRESH")))throw new IllegalStateException("Actor chunk has queued or loaded state: "+chunk+" "+status);
   for(var inbox:manager.vandorlabs$rampActorInbox())if(inbox.getPos().equals(chunk))throw new IllegalStateException("Actor chunk has a pending deserialized payload: "+chunk);
   if(manager.vandorlabs$rampActorSections().getExistingSectionsInChunk(key).anyMatch(section->!section.isEmpty()))throw new IllegalStateException("Actor chunk already has live sections: "+chunk);
  }
  private void touch(ChunkPos chunk){if(Math.abs((long)chunk.x)>1_875_000||Math.abs((long)chunk.z)>1_875_000)throw new IllegalArgumentException("Actor chunk coordinate bound");fresh(chunk);if(!touched.contains(chunk)&&touched.size()>=MAX_CHUNKS)throw new IllegalArgumentException("Ramp actor storage chunk budget");touched.add(chunk);}
  CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunk){
   touch(Objects.requireNonNull(chunk));if(reads.incrementAndGet()>MAX_PENDING_READS){reads.decrementAndGet();throw new IllegalStateException("Ramp actor storage read budget; retry after completion");}
   try{return regions.read(chunk).thenApply(raw->raw.map(tag->copy(tag,chunk))).whenComplete((value,failure)->reads.decrementAndGet());}catch(RuntimeException failure){reads.decrementAndGet();throw failure;}
  }
  Prepared prepareWrite(ChunkPos chunk,Optional<CompoundTag> expectedRaw,CompoundTag replacementRaw){
   touch(Objects.requireNonNull(chunk));Objects.requireNonNull(expectedRaw);Objects.requireNonNull(replacementRaw);int expectedBytes=expectedRaw.map(CompoundTag::sizeInBytes).orElse(0),replacementBytes=replacementRaw.sizeInBytes();if(expectedBytes>MAX_RAW_BYTES||replacementBytes>MAX_RAW_BYTES)throw new IllegalArgumentException("Ramp actor raw chunk byte budget");int bytes=512+expectedBytes+replacementBytes;if((long)stagedBytes+bytes>MAX_PREPARED_BYTES)throw new IllegalArgumentException("Ramp actor storage staged byte budget");var expected=expectedRaw.map(tag->copy(tag,chunk));var replacement=copy(replacementRaw,chunk);if(expected.isPresent())sameEnvelope(expected.get(),replacement);
   var verification=read(chunk).thenApply(actual->actual.equals(expected));var value=new Prepared(this,chunk,expected,replacement,verification,bytes,revisions.getOrDefault(chunk,0L));stagedBytes+=bytes;prepared.add(value);return value;
  }
  CompletableFuture<Void> sync(){
   active();for(var chunk:touched)fresh(chunk);long sequence=writeSequence;var pending=CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));return pending.thenCompose(ignored->regions.synchronize(true)).thenRun(()->durableSequence.accumulateAndGet(sequence,Math::max));
  }
  @Override public void close(){
   NativeRampRiders.requireThread(level);if(closed)return;closed=true;LIVE.remove(level,this);for(var value:new ArrayList<>(prepared))value.release();boolean unsynced=writeSequence!=durableSequence.get();writes.clear();touched.clear();revisions.clear();if(unsynced)throw new IllegalStateException("Ramp actor storage writes have no completed durability receipt; retain recovery intent");
  }
 }
 static final class Prepared implements AutoCloseable {
  private final Permit permit;private final ChunkPos chunk;private final CompletableFuture<Boolean> verification;private final int bytes;private final long revision;private Optional<CompoundTag> expected;private CompoundTag replacement;private boolean consumed;
  private Prepared(Permit permit,ChunkPos chunk,Optional<CompoundTag> expected,CompoundTag replacement,CompletableFuture<Boolean> verification,int bytes,long revision){this.permit=permit;this.chunk=chunk;this.expected=expected;this.replacement=replacement;this.verification=verification;this.bytes=bytes;this.revision=revision;}
  boolean ready(){permit.active();return verification.isDone();}
  Commit commit(){
   if(consumed)throw new IllegalStateException("Ramp actor prepared write consumed");permit.fresh(chunk);if(!verification.isDone())return new Commit(Status.PENDING,null,"");
   try{if(permit.revisions.getOrDefault(chunk,0L)!=revision||!verification.join()){release();return new Commit(Status.REFUSED,null,"Actor chunk changed since its expected raw record");}
    // Startup admission prevents normal main-thread entity stores between comparison and this enqueue.
    var writing=permit.regions.write(chunk,replacement);permit.writes.add(writing);permit.writeSequence++;permit.revisions.merge(chunk,1L,Long::sum);permit.entityStorage.vandorlabs$rampActorEmptyChunks().remove(chunk.toLong());release();return new Commit(Status.SUBMITTED,writing,"");
   }catch(RuntimeException failure){release();return new Commit(Status.REFUSED,null,message(failure));}
  }
  private void release(){if(consumed)return;consumed=true;expected=Optional.empty();replacement=null;if(permit.prepared.remove(this))permit.stagedBytes-=bytes;}
  @Override public void close(){permit.active();release();}
 }
 private static CompoundTag copy(CompoundTag raw,ChunkPos chunk){
  Objects.requireNonNull(raw);if(raw.sizeInBytes()>MAX_RAW_BYTES)throw new IllegalArgumentException("Ramp actor raw chunk byte budget");if(!raw.contains("Position",Tag.TAG_INT_ARRAY)||!Arrays.equals(raw.getIntArray("Position"),new int[]{chunk.x,chunk.z})||!raw.contains("DataVersion",Tag.TAG_INT)||!raw.contains("Entities",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown entity chunk envelope");var entities=(ListTag)raw.get("Entities");if(!entities.isEmpty()&&entities.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Unknown entity list type");return raw.copy();
 }
 private static void sameEnvelope(CompoundTag before,CompoundTag after){if(!before.getAllKeys().equals(after.getAllKeys()))throw new IllegalArgumentException("Raw actor rewrite changed unrelated chunk metadata");for(String key:before.getAllKeys())if(!key.equals("Entities")&&!Objects.equals(before.get(key),after.get(key)))throw new IllegalArgumentException("Raw actor rewrite changed unrelated chunk metadata");}
 private static String message(Throwable failure){while(failure instanceof CompletionException&&failure.getCause()!=null)failure=failure.getCause();String text=failure.getMessage();return text==null?failure.getClass().getSimpleName():text.substring(0,Math.min(512,text.length()));}
 private NativeRampActorStorage(){}
}
