package com.vandorlabs.neoforge.ramp;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.*;

/** Location-bound authority, never portable controller/item NBT. Chunk/save-data nonce mismatches deny. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampAuthorization {
 private static final String FILE="vandorlabs_ramp_authorization",PROOFS="NativeRampActionProofs";
 private static final int MAX_RECEIPTS=65536,MAX_CHUNK_PROOFS=4096;
 private record Receipt(UUID actor,UUID nonce,String type){}
 private static String type(NativeRampEntity owner){return net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType()).toString();}
 private static final Map<NativeRampEntity,UUID> BOUND=Collections.synchronizedMap(new WeakHashMap<>()),PENDING=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Set<NativeRampEntity> UNLOADED=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
 // Load events can run on a worker. Values contain only bounded scalars; no retained level/provider lambda.
 private static final Map<LevelChunk,Map<Long,UUID>> LOADED=Collections.synchronizedMap(new WeakHashMap<>());
 private static boolean main(NativeRampEntity owner){return owner!=null&&owner.getLevel() instanceof ServerLevel level&&level.getServer().isSameThread();}
 private static boolean installed(NativeRampEntity owner){if(!main(owner)||owner.inert()||owner.isRemoved())return false;var level=(ServerLevel)owner.getLevel();var pos=owner.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(pos)==owner;}
 private static Data data(ServerLevel level){return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new,Data::load,null),FILE);}
 public static boolean authorize(NativeRampEntity owner,ServerPlayer actor){
  if(!installed(owner)||actor==null||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,owner))return false;return grant(owner,actor);
 }
 static boolean grantInstalled(NativeRampEntity owner,ServerPlayer actor){return installed(owner)&&com.vandorlabs.neoforge.placement.NativeStationaryPlacement.mutating(owner.getLevel(),owner.getBlockPos())&&grant(owner,actor);}
 private static boolean grant(NativeRampEntity owner,ServerPlayer actor){if(!installed(owner)||actor==null||actor.level()!=owner.getLevel()||!actor.isAlive()||actor.isSpectator())return false;
  var store=data((ServerLevel)owner.getLevel());if(store.opaque!=null||!store.rows.containsKey(owner.getBlockPos().asLong())&&store.rows.size()>=MAX_RECEIPTS)return false;
  var receipt=new Receipt(actor.getUUID(),UUID.randomUUID(),type(owner));store.rows.put(owner.getBlockPos().asLong(),receipt);store.setDirty();BOUND.put(owner,receipt.nonce);PENDING.remove(owner);UNLOADED.remove(owner);owner.setChanged();return true;
 }
 public static UUID actorId(NativeRampEntity owner){if(!installed(owner)||UNLOADED.contains(owner))return null;var row=data((ServerLevel)owner.getLevel()).rows.get(owner.getBlockPos().asLong());return row!=null&&row.type.equals(type(owner))&&row.nonce.equals(BOUND.get(owner))?row.actor:null;}
 /** Released ramps use an offline permission actor; an online-player requirement would change automation. */
 public static ServerPlayer permissionActor(NativeRampEntity owner){var id=actorId(owner);if(id==null)throw new IllegalArgumentException("Reconfigure to authorize automation");return net.neoforged.neoforge.common.util.FakePlayerFactory.get((ServerLevel)owner.getLevel(),new GameProfile(id,"Automation"));}
 static void reading(NativeRampEntity owner){if(!main(owner))return;if(UNLOADED.remove(owner)){var previous=BOUND.remove(owner);if(previous!=null)PENDING.put(owner,previous);}else revoke(owner);}
 static void resumed(NativeRampEntity owner){
  if(!installed(owner))return;var level=(ServerLevel)owner.getLevel();UUID expected=PENDING.remove(owner);if(expected==null)expected=BOUND.get(owner);
  if(expected==null){var pos=owner.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);var proofs=LOADED.get(chunk);if(proofs!=null)expected=proofs.get(pos.asLong());}
  UNLOADED.remove(owner);var row=data(level).rows.get(owner.getBlockPos().asLong());if(expected!=null&&row!=null&&row.type.equals(type(owner))&&expected.equals(row.nonce))BOUND.put(owner,expected);else BOUND.remove(owner);
 }
 static void removed(NativeRampEntity owner){if(!UNLOADED.contains(owner))revoke(owner);}
 static void unloaded(NativeRampEntity owner){if(main(owner))UNLOADED.add(owner);}
 static void revoke(NativeRampEntity owner){if(!main(owner))return;UNLOADED.remove(owner);UUID nonce=BOUND.remove(owner),pending=PENDING.remove(owner);if(nonce==null)nonce=pending;if(nonce==null)return;var store=data((ServerLevel)owner.getLevel());var row=store.rows.get(owner.getBlockPos().asLong());if(row!=null&&nonce.equals(row.nonce)){store.rows.remove(owner.getBlockPos().asLong());store.setDirty();}}
 @SubscribeEvent public static void saving(ChunkDataEvent.Save event){
  if(!(event.getLevel() instanceof ServerLevel level)||!(event.getChunk() instanceof LevelChunk chunk))return;
  if(!level.getServer().isSameThread())throw new IllegalStateException("Ramp authority chunk publication requires the server thread");
  var proofs=new ListTag();for(var tile:chunk.getBlockEntities().values())if(tile instanceof NativeRampEntity owner&&!owner.isRemoved()){var nonce=BOUND.get(owner);if(nonce==null)nonce=PENDING.get(owner);if(nonce!=null){if(proofs.size()>=MAX_CHUNK_PROOFS)throw new IllegalStateException("Ramp authority chunk proof limit");var row=new CompoundTag();row.putLong("Position",owner.getBlockPos().asLong());row.putUUID("Nonce",nonce);proofs.add(row);}}
  if(!proofs.isEmpty())event.getData().put(PROOFS,proofs);else event.getData().remove(PROOFS);
 }
 @SubscribeEvent public static void loaded(ChunkDataEvent.Load event){
  if(!(event.getChunk() instanceof LevelChunk chunk))return;LOADED.remove(chunk);var input=event.getData();var proofs=new HashMap<Long,UUID>();
  if(input.contains(PROOFS,Tag.TAG_LIST)){var list=input.getList(PROOFS,Tag.TAG_COMPOUND);if(list.size()<=MAX_CHUNK_PROOFS)for(var element:list){var row=(CompoundTag)element;if(!row.contains("Position",Tag.TAG_LONG)||!row.hasUUID("Nonce")){proofs.clear();break;}long position=row.getLong("Position");var pos=BlockPos.of(position);if(pos.getX()>>4!=chunk.getPos().x||pos.getZ()>>4!=chunk.getPos().z||proofs.put(position,row.getUUID("Nonce"))!=null){proofs.clear();break;}}}
  if(!proofs.isEmpty())synchronized(LOADED){long bytes=160L+96L*proofs.size();for(var entry:LOADED.entrySet())if(entry.getKey()!=chunk)bytes+=160L+96L*entry.getValue().size();if(bytes<=8L*1024*1024)LOADED.put(chunk,Map.copyOf(proofs));}
 }
 @SubscribeEvent public static void worldUnload(LevelEvent.Unload event){if(!(event.getLevel() instanceof ServerLevel level))return;synchronized(BOUND){BOUND.keySet().removeIf(tile->tile.getLevel()==level);}synchronized(PENDING){PENDING.keySet().removeIf(tile->tile.getLevel()==level);}synchronized(UNLOADED){UNLOADED.removeIf(tile->tile.getLevel()==level);}synchronized(LOADED){LOADED.keySet().removeIf(chunk->chunk.getLevel()==level);}}
 private static final class Data extends SavedData {
  private final Map<Long,Receipt> rows=new HashMap<>();private CompoundTag opaque;
  static Data load(CompoundTag tag,HolderLookup.Provider registries){var data=new Data();try{if(!tag.contains("Schema",Tag.TAG_INT)||tag.getInt("Schema")!=1||!tag.contains("Receipts",Tag.TAG_LIST))throw new IllegalArgumentException();var list=tag.getList("Receipts",Tag.TAG_COMPOUND);if(list.size()>MAX_RECEIPTS||list.size()!=((ListTag)tag.get("Receipts")).size())throw new IllegalArgumentException();for(var element:list){var row=(CompoundTag)element;if(!row.contains("Position",Tag.TAG_LONG)||!row.hasUUID("Actor")||!row.hasUUID("Nonce")||!row.contains("Type",Tag.TAG_STRING)||net.minecraft.resources.ResourceLocation.tryParse(row.getString("Type"))==null||data.rows.put(row.getLong("Position"),new Receipt(row.getUUID("Actor"),row.getUUID("Nonce"),row.getString("Type")))!=null)throw new IllegalArgumentException();}}catch(RuntimeException failure){data.rows.clear();data.opaque=tag.copy();}return data;}
  @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){if(opaque!=null)return opaque.copy();tag.putInt("Schema",1);var list=new ListTag();for(var entry:rows.entrySet()){var row=new CompoundTag();row.putLong("Position",entry.getKey());row.putUUID("Actor",entry.getValue().actor);row.putUUID("Nonce",entry.getValue().nonce);row.putString("Type",entry.getValue().type);list.add(row);}tag.put("Receipts",list);return tag;}
 }
 private NativeRampAuthorization(){}
}
