package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.mixin.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;

/** Short physical-record checkpoint under the existing exact-operation initial actor fence.
 * All serialization stays in vanilla on the server thread; workers only force detached records. */
final class NativeRampActorCheckpoint {
 private final UUID operation;private final NativeRampActorIntent intent;
 private CompletableFuture<Void> entitySync,playerSync;private boolean saved;
 NativeRampActorCheckpoint(UUID operation,NativeRampActorIntent intent){this.operation=Objects.requireNonNull(operation);this.intent=Objects.requireNonNull(intent);if(!operation.equals(intent.operation()))throw new IllegalArgumentException("Actor checkpoint operation");}
 NativeRampJournal.Status poll(ServerLevel level){
  NativeRampRiders.requireThread(level);
  if(!saved){
   var chunks=new LinkedHashSet<ChunkPos>();var players=new ArrayList<ServerPlayer>();
   for(var participant:intent.participants()){
    var entity=level.getEntity(participant.id());
    if(entity==null||!NativeRampJournal.actorCommitHeld(level,operation,participant.id())||!entity.position().equals(participant.to())||!entity.getBoundingBox().equals(participant.body().move(participant.to().subtract(participant.from()))))throw new IllegalStateException("Ramp actor changed before record checkpoint");
    if(entity instanceof ServerPlayer player){if(!NativeRampPlayerHold.blocked(player))throw new IllegalStateException("Ramp participant physical fence changed");players.add(player);}
    else{chunks.add(chunk(participant.from()));chunks.add(chunk(participant.to()));}
   }
   if(chunks.size()>64||players.size()>NativeRampPlayerReceipts.MAX_PLAYERS)throw new IllegalStateException("Ramp actor checkpoint budget");
   var manager=(NativeRampEntityManagerAccess)(Object)((NativeRampEntityLevelAccess)(Object)level).vandorlabs$rampActorManager();
   for(var chunk:chunks)if(!manager.vandorlabs$rampStoreChunk(chunk.toLong(),entity->{}))return NativeRampJournal.Status.PENDING;
   for(var player:players){NativeRampSupportStamps.requireInitial(player,NativeRampJournal.supportedInitialOperation(level,operation));((NativeRampPlayerSaveAccess)(Object)level.getServer().getPlayerList()).vandorlabs$rampSaveParticipant(player);}
   var storage=(NativeRampEntityStorageAccess)(Object)manager.vandorlabs$rampActorStorage();entitySync=storage.vandorlabs$rampActorRegions().synchronize(true);
   saved=true;
  }
  if(playerSync==null){playerSync=NativeRampPlayerStorage.forceParticipants(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR),intent);if(playerSync==null)return NativeRampJournal.Status.PENDING;}
  if(!entitySync.isDone()||!playerSync.isDone())return NativeRampJournal.Status.PENDING;
  entitySync.join();playerSync.join();
  for(var participant:intent.participants()){var entity=level.getEntity(participant.id());if(entity==null||!NativeRampJournal.actorCommitHeld(level,operation,participant.id())||!entity.position().equals(participant.to()))throw new IllegalStateException("Ramp actor record fence or physical endpoint changed");}
  return NativeRampJournal.Status.READY;
 }
 private static ChunkPos chunk(net.minecraft.world.phys.Vec3 point){return new ChunkPos(net.minecraft.util.Mth.floor(point.x)>>4,net.minecraft.util.Mth.floor(point.z)>>4);}
}
