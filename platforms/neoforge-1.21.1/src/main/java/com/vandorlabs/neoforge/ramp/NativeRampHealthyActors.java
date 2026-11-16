package com.vandorlabs.neoforge.ramp;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Startup-only same-record support projection. Item payloads and unrelated native records stay opaque. */
final class NativeRampHealthyActors implements AutoCloseable {
 private final ServerLevel level;private final NativeRampJournal.Operation operation;private final NativeRampRuntime runtime;private final List<ChunkPos> chunks;private final Map<UUID,String> legacy=new HashMap<>();private NativeRampActorStorage.Permit permit;
 private int index,actors,rowsSeen;private final Set<UUID> identities=new HashSet<>();private CompletableFuture<Optional<CompoundTag>> reading;private Optional<CompoundTag> expected;private CompoundTag replacement;private NativeRampActorStorage.Prepared prepared;private CompletableFuture<Void> writing,sync;
 NativeRampHealthyActors(ServerLevel level,NativeRampJournal.Operation operation,NativeRampRuntime runtime){this.level=level;this.operation=operation;this.runtime=runtime;var chunks=new LinkedHashSet<ChunkPos>();for(var pos:operation.assembly().potential())for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})chunks.add(new ChunkPos(pos.offset(x,0,z)));if(chunks.size()>64)throw new IllegalArgumentException("Healthy actor support chunk bound");this.chunks=List.copyOf(chunks);for(var p:NativeRampJournal.legacyActors(operation))if(!p.type().equals("minecraft:player"))legacy.put(p.id(),p.type());if(legacy.size()>128)throw new IllegalArgumentException("Legacy healthy actor roster bound");}
 boolean poll(){
  if(index==chunks.size()){if(permit==null)return true;if(sync==null)sync=permit.sync();if(!sync.isDone())return false;sync.join();close();return true;}
  var chunk=chunks.get(index);
  if(expected==null){if(reading==null)reading=NativeRampActorStorage.inspect(level,chunk);if(!reading.isDone())return false;expected=reading.join();reading=null;replacement=project(chunk,expected);}
  if(replacement==null){expected=null;index++;return false;}
  if(writing==null){if(permit==null)permit=NativeRampActorStorage.acquire(level);if(prepared==null)prepared=permit.prepareWrite(chunk,expected,replacement);if(!prepared.ready())return false;var result=prepared.commit();if(result.status()==NativeRampActorStorage.Status.PENDING)return false;if(result.status()!=NativeRampActorStorage.Status.SUBMITTED)throw new IllegalStateException("Healthy actor support compare refused: "+result.reason());writing=result.completion();prepared=null;}
  if(!writing.isDone())return false;writing.join();writing=null;expected=null;replacement=null;index++;return false;
 }
 private CompoundTag project(ChunkPos chunk,Optional<CompoundTag> raw){
  if(raw.isEmpty())return null;var tag=raw.get();if(tag.getInt("DataVersion")!=SharedConstants.getCurrentVersion().getDataVersion().getVersion())throw new IllegalStateException("Unproven healthy actor data version retained");var rows=tag.getList("Entities",Tag.TAG_COMPOUND);rowsSeen+=rows.size();if(rowsSeen>8192)throw new IllegalStateException("Healthy actor chunk count bound");CompoundTag changed=null;
  var identity=NativeRampJournal.rootIdentity(level,operation);
  for(int i=0;i<rows.size();i++){
   var row=rows.getCompound(i);if(row.hasUUID("UUID")&&!identities.add(row.getUUID("UUID")))throw new IllegalArgumentException("Duplicate native actor UUID retained during healthy admission");if(row.hasUUID("UUID")&&legacy.containsKey(row.getUUID("UUID"))&&!row.getCompound("NeoForgeData").contains(NativeRampSupportStamp.KEY))requireLegacySupport(row,legacy.get(row.getUUID("UUID")));if(!row.getCompound("NeoForgeData").contains(NativeRampSupportStamp.KEY))continue;if(!row.getCompound("NeoForgeData").contains(NativeRampSupportStamp.KEY,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Unknown native actor support witness retained");var stamp=NativeRampSupportStamp.read(row.getCompound("NeoForgeData").getCompound(NativeRampSupportStamp.KEY));if(!stamp.operation.equals(operation.id()))continue;
   if(++actors>128||!stamp.root.equals(operation.root())||!stamp.owner.equals(identity.owner)||!Arrays.equals(stamp.recipe,NativeRampJournal.recipeIdentity(operation))||!stamp.dimension.equals(level.dimension().location().toString())||!row.hasUUID("UUID")||!row.getUUID("UUID").equals(stamp.actor)||!row.getString("id").equals(stamp.type))throw new IllegalArgumentException("Healthy actor witness identity/count retained");
   requireWitnessRecord(row,stamp);
   var delta=NativeRampSupportGeometry.rebase(operation.assembly(),stamp,runtime.frame.timeline.observed,runtime.frame.belt.observed());var position=stamp.position.add(delta);var targetChunk=new ChunkPos(net.minecraft.util.Mth.floor(position.x)>>4,net.minecraft.util.Mth.floor(position.z)>>4);if(!targetChunk.equals(chunk))throw new IllegalArgumentException("Cross-chunk healthy actor adoption requires a separate durable handoff");
   var updated=new NativeRampSupportStamp(stamp.actor,stamp.type,stamp.dimension,stamp.operation,stamp.owner,stamp.root,stamp.recipe,runtime.frame.revision,runtime.frame.timeline.observed,runtime.frame.belt.observed(),position,stamp.body.move(delta));var next=row.copy();var pos=new ListTag();pos.add(DoubleTag.valueOf(position.x));pos.add(DoubleTag.valueOf(position.y));pos.add(DoubleTag.valueOf(position.z));next.put("Pos",pos);next.getCompound("NeoForgeData").put(NativeRampSupportStamp.KEY,updated.write());if(!next.equals(row)){if(changed==null)changed=tag.copy();changed.getList("Entities",Tag.TAG_COMPOUND).set(i,next);}
  }
  return changed;
 }
 /** A support witness authenticates position, not a forced canonical Ground/Fall observation. */
 static void requireWitnessRecord(CompoundTag row,NativeRampSupportStamp stamp){var motion=row.getList("Motion",Tag.TAG_DOUBLE);if(motion.size()!=3)throw new IllegalArgumentException("Healthy actor velocity retained");var participant=new NativeRampActorIntent.Participant(stamp.actor,stamp.type,stamp.position,stamp.position,stamp.body,new Vec3(motion.getDouble(0),motion.getDouble(1),motion.getDouble(2)),row.getFloat("FallDistance"),row.getBoolean("OnGround"),false,false,false);if(NativeRampActorRecord.classify(row,participant).state()!=NativeRampActorRecord.State.ORIGINAL)throw new IllegalArgumentException("Healthy actor native role or support record retained");}
 private void requireLegacySupport(CompoundTag row,String type){var entityType=type.equals("minecraft:item")?net.minecraft.world.entity.EntityType.ITEM:type.equals("minecraft:pig")?net.minecraft.world.entity.EntityType.PIG:null;if(entityType==null||!row.getString("id").equals(type))throw new IllegalArgumentException("Legacy native actor role changed");var pos=row.getList("Pos",Tag.TAG_DOUBLE);var motion=row.getList("Motion",Tag.TAG_DOUBLE);if(pos.size()!=3||motion.size()!=3)throw new IllegalArgumentException("Legacy native actor physical record retained");var p=new Vec3(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2));var body=entityType.getDimensions().makeBoundingBox(p.x,p.y,p.z);var actor=new NativeRampActorIntent.Participant(row.getUUID("UUID"),type,p,p,body,new Vec3(motion.getDouble(0),motion.getDouble(1),motion.getDouble(2)),row.getFloat("FallDistance"),row.getBoolean("OnGround"),false,false,false);if(NativeRampActorRecord.classify(row,actor).state()!=NativeRampActorRecord.State.ORIGINAL||!NativeRampSupportGeometry.supported(operation.assembly(),runtime.frame.timeline.observed,runtime.frame.belt.observed(),body))throw new IllegalArgumentException("Legacy ACTIVE actor has no authenticated support at saved root pose; retained");}
 @Override public void close(){if(prepared!=null){prepared.close();prepared=null;}if(permit!=null){permit.close();permit=null;}}
}
