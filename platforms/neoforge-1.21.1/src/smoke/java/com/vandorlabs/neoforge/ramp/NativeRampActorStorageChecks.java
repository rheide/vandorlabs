package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.mixin.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.storage.*;
import net.minecraft.world.level.entity.ChunkEntities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;

/** Real existing-worker IO during startup only; no entity factory, gameplay or actor-resolution acceptance. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampActorStorageChecks {
 private static final ChunkPos CHUNK=new ChunkPos(12000,-12000);private static final UUID ACTOR=UUID.fromString("714ae4bd-cc76-48b5-b83e-2c2304eb6010");private static boolean ran;private static int checks;
 private static String phase(){return System.getProperty("vandorlabs.transmitterPhase","");}private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Ramp raw actor storage: "+why);}
 private static void refuses(Runnable work,String why){boolean refused=false;try{work.run();}catch(IllegalArgumentException|IllegalStateException expected){refused=true;}need(refused,why);}
 private static <T>T waitFor(CompletableFuture<T> future)throws Exception{return future.get(15,TimeUnit.SECONDS);}
 private static void ready(NativeRampActorStorage.Prepared value)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!value.ready()){if(System.nanoTime()>end)throw new AssertionError("Actor storage verification timeout");Thread.sleep(1);}}
 private static ListTag numbers(double... values){var list=new ListTag();for(double value:values)list.add(DoubleTag.valueOf(value));return list;}
 private static CompoundTag raw(double xOffset){var root=new CompoundTag();root.putInt("DataVersion",SharedConstants.getCurrentVersion().getDataVersion().getVersion());root.putIntArray("Position",new int[]{CHUNK.x,CHUNK.z});root.putString("UnknownRootField","preserve exactly");var actor=new CompoundTag();actor.putString("id","minecraft:pig");actor.putUUID("UUID",ACTOR);actor.put("Pos",numbers(CHUNK.x*16D+.5+xOffset,100.75,CHUNK.z*16D+.5));actor.put("Motion",numbers(.01,0,.02));actor.putFloat("FallDistance",0);actor.putBoolean("OnGround",true);var opaque=new CompoundTag();opaque.putInt("DiamondCount",11);opaque.putString("PrivateCredential","opaque source bytes");actor.put("ForgeCaps",opaque);var future=new ListTag();future.add(StringTag.valueOf("do not decode"));actor.put("FutureInventory",future);var entities=new ListTag();entities.add(actor);root.put("Entities",entities);return root;}
 @SubscribeEvent public static void about(ServerAboutToStartEvent event){if(phase().startsWith("ramp-actor-storage-"))NativeRampRecoveryAdmission.storageProbe=level->{if(level.dimension()==Level.OVERWORLD)try{run(level);}catch(Exception failure){throw new IllegalStateException("Raw actor storage fixture",failure);}};}
 private static final class Barrier implements AutoCloseable {
  final CountDownLatch release=new CountDownLatch(1);
  @SuppressWarnings("unchecked") Barrier(NativeRampEntityStorageAccess storage)throws Exception{Field workerField=SimpleRegionStorage.class.getDeclaredField("worker");workerField.setAccessible(true);var worker=(IOWorker)workerField.get(storage.vandorlabs$rampActorRegions());Field mailboxField=IOWorker.class.getDeclaredField("mailbox");mailboxField.setAccessible(true);var mailbox=(ProcessorMailbox<StrictQueue.IntRunnable>)mailboxField.get(worker);var entered=new CountDownLatch(1);mailbox.tell(new StrictQueue.IntRunnable(0,()->{entered.countDown();try{if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("actor budget barrier timeout");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}}));need(entered.await(15,TimeUnit.SECONDS),"real worker write-budget barrier");}
  public void close(){release.countDown();}
 }
 private static void queuedBudget(NativeRampActorStorage.Permit permit,NativeRampEntityStorageAccess storage,int count,int bytes,int firstChunk,int accepted)throws Exception{
  var values=new ArrayList<NativeRampActorStorage.Prepared>();for(int i=0;i<count;i++){var pos=new ChunkPos(CHUNK.x+firstChunk+i,CHUNK.z);var raw=new CompoundTag();raw.putInt("DataVersion",SharedConstants.getCurrentVersion().getDataVersion().getVersion());raw.putIntArray("Position",new int[]{pos.x,pos.z});raw.put("Entities",new ListTag());if(bytes>0)raw.putByteArray("OpaqueChunkMetadata",new byte[bytes]);var value=permit.prepareWrite(pos,Optional.empty(),raw);ready(value);values.add(value);}
  var writes=new ArrayList<CompletableFuture<Void>>();try(var barrier=new Barrier(storage)){for(int i=0;i<accepted;i++){var result=values.get(i).commit();need(result.status()==NativeRampActorStorage.Status.SUBMITTED,"queued write within capacity");writes.add(result.completion());}need(values.get(accepted).commit().status()==NativeRampActorStorage.Status.PENDING,"blocked queued write preserves pending count/byte budget");need(values.get(accepted).commit().status()==NativeRampActorStorage.Status.PENDING,"budget retry cannot enqueue twice");}
  for(var write:writes)waitFor(write);for(int i=accepted;i<count;i++){var result=values.get(i).commit();need(result.status()==NativeRampActorStorage.Status.SUBMITTED,"write retry succeeds after actual worker completion");waitFor(result.completion());}waitFor(permit.sync());
 }
 private static void run(ServerLevel level)throws Exception{
  ran=true;int chunks=level.getChunkSource().getLoadedChunksCount();need(level.getEntity(ACTOR)==null,"probe actor never instantiated");
  var manager=(NativeRampEntityManagerAccess)(Object)((NativeRampEntityLevelAccess)(Object)level).vandorlabs$rampActorManager();var storage=(NativeRampEntityStorageAccess)(Object)manager.vandorlabs$rampActorStorage();
  try(var permit=NativeRampActorStorage.acquire(level)){
   refuses(()->NativeRampActorStorage.acquire(level),"nested storage scope refused");need(waitFor(CompletableFuture.supplyAsync(()->{try{permit.read(CHUNK);return false;}catch(IllegalStateException correct){return true;}})),"worker world access refused");
   if(phase().endsWith("write")){
    need(waitFor(permit.read(CHUNK)).isEmpty(),"fresh raw region absent");
    Field workerField=SimpleRegionStorage.class.getDeclaredField("worker");workerField.setAccessible(true);var worker=(IOWorker)workerField.get(storage.vandorlabs$rampActorRegions());Field mailboxField=IOWorker.class.getDeclaredField("mailbox");mailboxField.setAccessible(true);@SuppressWarnings("unchecked")var mailbox=(ProcessorMailbox<StrictQueue.IntRunnable>)mailboxField.get(worker);
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);mailbox.tell(new StrictQueue.IntRunnable(0,()->{entered.countDown();try{if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("actor IO barrier timeout");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}}));need(entered.await(15,TimeUnit.SECONDS),"real shared IO worker blocked");
    var caller=raw(0);NativeRampActorStorage.Prepared first;try{first=permit.prepareWrite(CHUNK,Optional.empty(),caller);caller.putString("UnknownRootField","mutated caller");need(!first.ready()&&first.commit().status()==NativeRampActorStorage.Status.PENDING,"pending real IO does not submit or refuse");}finally{release.countDown();}
    ready(first);storage.vandorlabs$rampActorEmptyChunks().add(CHUNK.toLong());var submitted=first.commit();need(submitted.status()==NativeRampActorStorage.Status.SUBMITTED,"verified raw write queued");need(!storage.vandorlabs$rampActorEmptyChunks().contains(CHUNK.toLong()),"vanilla stale empty cache invalidated");waitFor(submitted.completion());waitFor(permit.sync());need(waitFor(permit.read(CHUNK)).orElseThrow().equals(raw(0)),"owned immutable snapshot and opaque fields exact");
    var returned=waitFor(permit.read(CHUNK)).orElseThrow();returned.putString("UnknownRootField","caller read mutation");need(waitFor(permit.read(CHUNK)).orElseThrow().equals(raw(0)),"returned raw snapshot does not mutate worker storage");
    try(var wrong=permit.prepareWrite(CHUNK,Optional.of(raw(.5)),raw(.75))){ready(wrong);need(wrong.commit().status()==NativeRampActorStorage.Status.REFUSED,"changed expected raw record refuses");}
    var a=permit.prepareWrite(CHUNK,Optional.of(raw(0)),raw(.25));var b=permit.prepareWrite(CHUNK,Optional.of(raw(0)),raw(.5));ready(a);ready(b);var write=a.commit();need(write.status()==NativeRampActorStorage.Status.SUBMITTED,"first prepared mutation submitted");waitFor(write.completion());need(b.commit().status()==NativeRampActorStorage.Status.REFUSED,"same-scope old verification cannot overwrite later write");waitFor(permit.sync());
    queuedBudget(permit,storage,9,0,8,8);queuedBudget(permit,storage,5,7*512*1024,32,4);
   }
   need(waitFor(permit.read(CHUNK)).orElseThrow().equals(raw(.25)),"fresh process exact complete raw record");
   var unrelated=raw(.5);unrelated.putString("UnknownRootField","lost metadata");refuses(()->permit.prepareWrite(CHUNK,Optional.of(raw(.25)),unrelated),"unknown root metadata never rewritten");var invalid=raw(.25);invalid.putIntArray("Position",new int[]{0,0});refuses(()->permit.prepareWrite(CHUNK,Optional.empty(),invalid),"misplaced record refused");var badList=raw(.25);var strings=new ListTag();strings.add(StringTag.valueOf("wrong"));badList.put("Entities",strings);refuses(()->permit.prepareWrite(CHUNK,Optional.empty(),badList),"unknown entity list type refused");var huge=raw(.25);huge.putByteArray("Large",new byte[NativeRampActorStorage.MAX_RAW_BYTES]);refuses(()->permit.prepareWrite(CHUNK,Optional.empty(),huge),"raw byte bound before snapshot copy");
   @SuppressWarnings("rawtypes")Long2ObjectMap statuses=manager.vandorlabs$rampActorLoads();Object fresh=statuses.defaultReturnValue();for(Object status:((Enum<?>)fresh).getDeclaringClass().getEnumConstants())if(!((Enum<?>)status).name().equals("FRESH")){boolean present=statuses.containsKey(CHUNK.toLong());Object old=statuses.get(CHUNK.toLong());try{statuses.put(CHUNK.toLong(),status);refuses(()->permit.read(CHUNK),"pending/loaded chunk refuses: "+status);}finally{if(present)statuses.put(CHUNK.toLong(),old);else statuses.remove(CHUNK.toLong());}}
   var inbox=new ChunkEntities<net.minecraft.world.entity.Entity>(CHUNK,List.of());manager.vandorlabs$rampActorInbox().add(inbox);try{refuses(()->permit.read(CHUNK),"already deserialized inbox payload refuses");}finally{manager.vandorlabs$rampActorInbox().remove(inbox);}
   need(waitFor(permit.read(CHUNK)).orElseThrow().equals(raw(.25)),"all refusals preserve exact actor/capability/inventory records");waitFor(permit.sync());
  }
  need(level.getChunkSource().getLoadedChunksCount()==chunks&&level.getEntity(ACTOR)==null,"raw IO neither loads chunks nor instantiates entities");
 }
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!phase().startsWith("ramp-actor-storage-"))return;need(ran,"actual isolated startup probe ran");refuses(()->NativeRampActorStorage.acquire(event.getServer().overworld()),"normal started server cannot acquire raw actor authority");System.out.println("[vandorlabs][ramp-actor-storage] "+(phase().endsWith("write")?"write":"read")+" PASS checks="+checks+" actual existing-worker pending/compare/write/sync/restart opaque records; fresh-only IO; pending/loaded/inbox refused; actor recovery policy separate");event.getServer().halt(false);}
 private NativeRampActorStorageChecks(){}
}
