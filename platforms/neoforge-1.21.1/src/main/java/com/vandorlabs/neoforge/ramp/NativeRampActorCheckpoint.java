package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.mixin.*;
import com.vandorlabs.neoforge.transfer.NativeEntityCheckpoint;
import net.minecraft.nbt.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;

/** Short physical-record checkpoint under the existing exact-operation initial actor fence.
 * All serialization stays in vanilla on the server thread; workers only force detached records. */
final class NativeRampActorCheckpoint {
 private final UUID operation;private final NativeRampActorIntent intent;private final Map<UUID,NativeRampActorIntent.Participant> nativeActors;
 private CompletableFuture<Void> playerSync;private NativeEntityCheckpoint entities;private boolean saved;private final boolean returning;
 NativeRampActorCheckpoint(UUID operation,NativeRampActorIntent intent){this(operation,intent,false);}
 NativeRampActorCheckpoint(UUID operation,NativeRampActorIntent intent,boolean returning){this.returning=returning;this.operation=Objects.requireNonNull(operation);this.intent=Objects.requireNonNull(intent);if(!operation.equals(intent.operation()))throw new IllegalArgumentException("Actor checkpoint operation");var actors=new HashMap<UUID,NativeRampActorIntent.Participant>();for(var participant:intent.participants())if(!participant.type().equals("minecraft:player"))actors.put(participant.id(),participant);nativeActors=Map.copyOf(actors);}
 NativeRampJournal.Status poll(ServerLevel level){
  NativeRampRiders.requireThread(level);if(level.noSave)return NativeRampJournal.Status.PENDING;
  if(!saved){
   requireParticipants(level);var chunks=new LinkedHashSet<ChunkPos>();var players=new ArrayList<ServerPlayer>();
   for(var participant:intent.participants()){
    var entity=level.getEntity(participant.id());
    if(entity==null||!NativeRampJournal.actorCommitHeld(level,operation,participant.id())||!entity.position().equals(participant.to())||!entity.getBoundingBox().equals(participant.body().move(participant.to().subtract(participant.from()))))throw new IllegalStateException("Ramp actor changed before record checkpoint");
    if(entity instanceof ServerPlayer player){if(!NativeRampPlayerHold.blocked(player))throw new IllegalStateException("Ramp participant physical fence changed");players.add(player);}
    else{chunks.add(chunk(participant.from()));chunks.add(chunk(participant.to()));}
   }
   if(chunks.size()>64||players.size()>NativeRampPlayerReceipts.MAX_PLAYERS)throw new IllegalStateException("Ramp actor checkpoint budget");
   if(!chunks.isEmpty())entities=new NativeEntityCheckpoint(level,chunks,()->requireParticipants(level),64,(chunk,record)->requireRecord(level,chunk,record));
   for(var player:players){if(!returning)NativeRampSupportStamps.requireInitial(player,NativeRampJournal.supportedInitialOperation(level,operation));((NativeRampPlayerSaveAccess)(Object)level.getServer().getPlayerList()).vandorlabs$rampSaveParticipant(player);}
   saved=true;
  }
  if(entities!=null&&!entities.poll())return NativeRampJournal.Status.PENDING;
  if(playerSync==null){playerSync=NativeRampPlayerStorage.forceParticipants(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR),intent);if(playerSync==null)return NativeRampJournal.Status.PENDING;}
  if(!playerSync.isDone())return NativeRampJournal.Status.PENDING;
  playerSync.join();
  for(var participant:intent.participants()){var entity=level.getEntity(participant.id());if(entity==null||!NativeRampJournal.actorCommitHeld(level,operation,participant.id())||!entity.position().equals(participant.to()))throw new IllegalStateException("Ramp actor record fence or physical endpoint changed");}
  return NativeRampJournal.Status.READY;
 }
 private void requireParticipants(ServerLevel level){for(var participant:intent.participants()){var entity=level.getEntity(participant.id());if(entity==null||!NativeRampJournal.actorCommitHeld(level,operation,participant.id())||!entity.position().equals(participant.to())||!entity.getBoundingBox().equals(participant.body().move(participant.to().subtract(participant.from()))))throw new IllegalStateException("Ramp actor changed during native record checkpoint");}}
 private void requireRecord(ServerLevel level,ChunkPos chunk,CompoundTag record){
  var rows=record==null?new ListTag():record.getList("Entities",Tag.TAG_COMPOUND);var found=new HashMap<UUID,CompoundTag>();
  for(var raw:rows){var row=(CompoundTag)raw;if(row.hasUUID("UUID")){var id=row.getUUID("UUID");if(nativeActors.containsKey(id)&&found.put(id,row)!=null)throw new IllegalStateException("Ramp participant native record is duplicated");}}
  for(var participant:nativeActors.values()){var row=found.get(participant.id());boolean expected=chunk.equals(chunk(participant.to()));if((row!=null)!=expected)throw new IllegalStateException("Ramp participant native record is missing or unexpectedly present");if(row==null)continue;var pos=row.getList("Pos",Tag.TAG_DOUBLE);if(pos.size()!=3||pos.getDouble(0)!=participant.to().x||pos.getDouble(1)!=participant.to().y||pos.getDouble(2)!=participant.to().z||NativeRampActorRecord.classify(row,participant).state()==NativeRampActorRecord.State.REFUSED)throw new IllegalStateException("Ramp participant native record changed");var entity=level.getEntity(participant.id());if(entity instanceof net.minecraft.world.entity.item.ItemEntity item&&!row.getCompound("Item").equals(com.vandorlabs.neoforge.inventory.NativeInventoryItems.write(item.getItem(),level.registryAccess())))throw new IllegalStateException("Ramp participant item ownership changed in native record");}
 }
 private static ChunkPos chunk(net.minecraft.world.phys.Vec3 point){return new ChunkPos(net.minecraft.util.Mth.floor(point.x)>>4,net.minecraft.util.Mth.floor(point.z)>>4);}
}
