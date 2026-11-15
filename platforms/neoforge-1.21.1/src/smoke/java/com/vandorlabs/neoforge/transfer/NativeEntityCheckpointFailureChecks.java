package com.vandorlabs.neoforge.transfer;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual native entity serialization omission and asynchronous region failure cannot produce READY. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeEntityCheckpointFailureChecks {
 private static final BlockPos ROOT=new BlockPos(45000,120,45000);private static final UUID ID=new UUID(210,9901);
 private static ServerLevel world;private static NativeEntityCheckpoint checkpoint;private static BadSaveItem item;private static Path badRegion;private static int phase,ticks,checks;private static boolean running,omit;private static java.util.concurrent.CompletableFuture<?> faultReady;
 private static boolean enabled(){return Boolean.getBoolean("vandorlabs.entityCheckpointFailure");}
 private static final class BadSaveItem extends ItemEntity {BadSaveItem(ServerLevel level){super(level,ROOT.getX()+.5,ROOT.getY(),ROOT.getZ()+.5,new ItemStack(Items.GOLD_INGOT,11));}public void addAdditionalSaveData(CompoundTag tag){if(omit)throw new IllegalStateException("fixture native entity serializer refusal");super.addAdditionalSaveData(tag);}}
 private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native entity checkpoint phase="+phase+" tick="+ticks+": "+why);}
 private static void authority(){need(running&&item!=null&&!item.isRemoved()&&item.getItem().is(Items.GOLD_INGOT)&&item.getItem().getCount()==11,"exact live actor custody retained");}
 private static void records(ChunkPos chunk,CompoundTag tag){int found=0;for(var raw:tag==null?new ListTag():tag.getList("Entities",Tag.TAG_COMPOUND)){var row=(CompoundTag)raw;if(row.hasUUID("UUID")&&ID.equals(row.getUUID("UUID"))){found++;need(row.getString("id").equals("minecraft:item")&&row.getCompound("Item").equals(item.getItem().save(world.registryAccess())),"expected actor type/resource record");}}if(found!=1)throw new IllegalStateException("fixture expected actor omitted");}
 private static NativeEntityCheckpoint checkpoint(){return new NativeEntityCheckpoint(world,List.of(new ChunkPos(ROOT)),NativeEntityCheckpointFailureChecks::authority,64,NativeEntityCheckpointFailureChecks::records);}
 @SubscribeEvent public static void start(ServerStartedEvent event){if(!enabled())return;try{world=event.getServer().overworld();com.vandorlabs.neoforge.ramp.NativeRampCheckpointRoleChecks.run(world);world.setChunkForced(ROOT.getX()>>4,ROOT.getZ()>>4,true);world.getChunkAt(ROOT);checkpoint=new NativeEntityCheckpoint(world,List.of(new ChunkPos(ROOT)),()->{});running=true;}catch(Throwable failure){fail(failure);}}
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!running)return;try{need(++ticks<600,"bounded real I/O wait");if(phase==0){if(!checkpoint.loaded())return;item=new BadSaveItem(world);item.setUUID(ID);item.setNoGravity(true);item.setInvulnerable(true);item.setPickUpDelay(32767);need(world.addFreshEntity(item),"actual native actor installed");omit=true;checkpoint=checkpoint();phase=1;return;}
 if(phase==1){try{need(!checkpoint.poll(),"omitted actor must never READY");}catch(IllegalStateException expected){need(expected.getMessage().equals("fixture expected actor omitted"),"omission caught by copied native record validator");omit=false;var chunk=new ChunkPos(ROOT);badRegion=world.getServer().getWorldPath(LevelResource.ROOT).resolve("entities/r."+(chunk.x>>5)+"."+(chunk.z>>5)+".mca");faultReady=prepareNativeIoFault(chunk);phase=4;}return;}
 if(phase==4){if(!faultReady.isDone())return;faultReady.join();need(Files.isDirectory(badRegion),"exact test region replaced on native I/O queue");checkpoint=checkpoint();phase=2;return;}
 if(phase==2){try{need(!checkpoint.poll(),"failed native write must never READY");}catch(java.util.concurrent.CompletionException expected){Throwable cause=expected;while(cause.getCause()!=null)cause=cause.getCause();need(cause instanceof java.io.IOException,"actual native region I/O failure propagated");Files.delete(badRegion);badRegion=null;checkpoint=checkpoint();phase=3;}return;}
 if(phase==3){if(!checkpoint.poll())return;authority();need(Files.isRegularFile(world.getServer().getWorldPath(LevelResource.ROOT).resolve("entities/r."+(new ChunkPos(ROOT).x>>5)+"."+(new ChunkPos(ROOT).z>>5)+".mca")),"retry publishes actual native actor file");running=false;System.out.println("[vandorlabs][entity-checkpoint-failure] PASS checks="+checks+" nativeOmissionRefused/actualFailedWriteFuture/noREADY/retry/oneOwner11");world.getServer().saveEverything(true,true,true);world.getServer().halt(false);}
 }catch(Throwable failure){fail(failure);}}
 @SuppressWarnings("unchecked") private static java.util.concurrent.CompletableFuture<?> prepareNativeIoFault(ChunkPos chunk)throws Exception{
  var manager=(com.vandorlabs.neoforge.mixin.NativeRampEntityManagerAccess)(Object)((com.vandorlabs.neoforge.mixin.NativeRampEntityLevelAccess)(Object)world).vandorlabs$rampActorManager();
  var regions=((com.vandorlabs.neoforge.mixin.NativeRampEntityStorageAccess)(Object)manager.vandorlabs$rampActorStorage()).vandorlabs$rampActorRegions();
  var workerField=regions.getClass().getDeclaredField("worker");workerField.setAccessible(true);var worker=workerField.get(regions);
  var submit=worker.getClass().getDeclaredMethod("submitTask",java.util.function.Supplier.class);submit.setAccessible(true);
  return (java.util.concurrent.CompletableFuture<?>)submit.invoke(worker,(java.util.function.Supplier<com.mojang.datafixers.util.Either<Void,Exception>>)()->{try{
   var storageField=worker.getClass().getDeclaredField("storage");storageField.setAccessible(true);var storage=storageField.get(worker);var cacheField=storage.getClass().getDeclaredField("regionCache");cacheField.setAccessible(true);
   var cache=(it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<net.minecraft.world.level.chunk.storage.RegionFile>)cacheField.get(storage);var old=cache.remove(ChunkPos.asLong(chunk.x>>5,chunk.z>>5));if(old!=null)old.close();
   Files.deleteIfExists(badRegion);Files.createDirectories(badRegion);return com.mojang.datafixers.util.Either.left(null);
  }catch(Exception failure){return com.mojang.datafixers.util.Either.right(failure);}});
 }
 private static void fail(Throwable failure){running=false;omit=false;try{if(badRegion!=null)Files.deleteIfExists(badRegion);}catch(Exception cleanup){failure.addSuppressed(cleanup);}failure.printStackTrace();System.err.println("[vandorlabs][entity-checkpoint-failure] FAIL "+failure);if(world!=null)world.getServer().halt(false);}
 private NativeEntityCheckpointFailureChecks(){}
}
