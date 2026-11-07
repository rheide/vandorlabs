package com.vandorlabs.neoforge.ramp;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Narrow startup return: one cold entity chunk, no neighbouring actors or queued payloads. */
final class NativeRampColdActorRecovery implements AutoCloseable {
 private final ServerLevel level;private final NativeRampJournal.Operation operation;private final NativeRampActorIntent intent;private NativeRampActorStorage.Permit permit;
 private final ChunkPos actorChunk;private final List<ChunkPos> inspect;private final Map<UUID,NativeRampActorIntent.Participant> actors;private int phase,index;
 private CompletableFuture<Optional<CompoundTag>> reading;private Optional<CompoundTag> expected;private CompoundTag replacement;private NativeRampActorStorage.Prepared prepared;private CompletableFuture<Void> completion;
 NativeRampColdActorRecovery(ServerLevel level,List<NativeRampJournal.Operation> operations){
  this.level=level;if(operations.size()!=1)throw new IllegalStateException("Cold actor repair supports one operation at a time");operation=operations.getFirst();intent=NativeRampJournal.startupActorIntent(operation);if(!NativeRampJournal.knownActorRecoveryHeader(operation)||intent==null||intent.participants().size()>128)throw new IllegalStateException("Unproven actor recovery header/count retained");
  var map=new HashMap<UUID,NativeRampActorIntent.Participant>();ChunkPos selected=null;long work=0;for(var actor:intent.participants()){var from=chunk(actor.from());if(!from.equals(chunk(actor.to()))||selected!=null&&!selected.equals(from))throw new IllegalStateException("Cross-record actor ownership requires a separate durable handoff");selected=from;map.put(actor.id(),actor);var body=actor.body().deflate(1e-7);for(var source:operation.assembly().originalAlignment().footprint().origins())for(var box:source.shapes()){if(++work>1_048_576)throw new IllegalStateException("Actor original-shape validation budget");var p=source.position();if(body.maxX>p.getX()+box.minX&&body.minX<p.getX()+box.maxX&&body.maxY>p.getY()+box.minY&&body.minY<p.getY()+box.maxY&&body.maxZ>p.getZ()+box.minZ&&body.minZ<p.getZ()+box.maxZ)throw new IllegalStateException("Actor original body overlaps captured sources");}}
  actorChunk=Objects.requireNonNull(selected);actors=Map.copyOf(map);var chunks=new ArrayList<ChunkPos>();chunks.add(actorChunk);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)chunks.add(new ChunkPos(actorChunk.x+x,actorChunk.z+z));inspect=List.copyOf(chunks);permit=NativeRampActorStorage.acquire(level);
 }
 NativeRampJournal.Status poll(){
  NativeRampRiders.requireThread(level);
  if(phase==0){if(reading==null)reading=permit.read(inspect.get(index));if(!reading.isDone())return NativeRampJournal.Status.PENDING;var raw=reading.join();reading=null;if(index==0)inspectActors(raw);else if(raw.isPresent()&&!raw.get().getList("Entities",Tag.TAG_COMPOUND).isEmpty())throw new IllegalStateException("Cold actor repair requires an empty neighbouring entity chunk "+inspect.get(index)+" count="+raw.get().getList("Entities",Tag.TAG_COMPOUND).size());if(++index<inspect.size())return NativeRampJournal.Status.PENDING;phase=1;}
  if(phase==1){if(replacement!=null){if(prepared==null)prepared=permit.prepareWrite(actorChunk,expected,replacement);if(!prepared.ready())return NativeRampJournal.Status.PENDING;var receipt=prepared.commit();if(receipt.status()==NativeRampActorStorage.Status.PENDING)return NativeRampJournal.Status.PENDING;if(receipt.status()!=NativeRampActorStorage.Status.SUBMITTED)throw new IllegalStateException("Actor record compare refused: "+receipt.reason());completion=receipt.completion();prepared=null;phase=2;}else phase=3;expected=null;replacement=null;}
  if(phase==2){if(!completion.isDone())return NativeRampJournal.Status.PENDING;completion.join();completion=null;phase=3;}
  if(phase==3){if(completion==null)completion=permit.sync();if(!completion.isDone())return NativeRampJournal.Status.PENDING;completion.join();completion=null;permit.close();permit=null;phase=4;}
  if(phase==4){var chunks=new LinkedHashSet<ChunkPos>();for(var pos:NativeRampJournal.startupCoordinates(operation))chunks.add(new ChunkPos(pos));if(chunks.size()>64)throw new IllegalStateException("Actor source recovery chunk budget");for(var pos:chunks)level.getChunk(pos.x,pos.z);int boxes=0;for(var actor:actors.values()){var body=actor.body().deflate(1e-7);for(var shape:level.getBlockCollisions(null,body))for(var box:shape.toAabbs()){if(++boxes>131072)throw new IllegalStateException("Actor original collision query budget");if(box.intersects(body))throw new IllegalStateException("Actor original space is obstructed; operation retained");}}
   NativeRampJournal.startupActorsReturned(level,operation,intent);phase=5;}
  if(phase==5){var result=NativeRampJournal.recover(level,operation);if(result==NativeRampJournal.Status.REFUSED)throw new IllegalStateException("Actor source restore retained: "+operation.error());if(result!=NativeRampJournal.Status.DONE)return NativeRampJournal.Status.PENDING;NativeRampJournal.startupActorRecoveryComplete(level);phase=6;}
  return NativeRampJournal.Status.READY;
 }
 private void inspectActors(Optional<CompoundTag> raw){
  if(raw.isEmpty())throw new IllegalStateException("Actor records are missing; operation retained");var chunk=raw.get();if(chunk.getInt("DataVersion")!=SharedConstants.getCurrentVersion().getDataVersion().getVersion())throw new IllegalStateException("Unproven actor chunk data version retained");var entities=chunk.getList("Entities",Tag.TAG_COMPOUND);if(entities.size()!=actors.size())throw new IllegalStateException("Cold actor repair cannot rewrite unrelated or missing actor records count="+entities.size()+" expected="+actors.size());var found=new HashSet<UUID>();CompoundTag changed=null;
  for(int i=0;i<entities.size();i++){var row=entities.getCompound(i);if(!row.hasUUID("UUID"))throw new IllegalStateException("Actor UUID unavailable");var actor=actors.get(row.getUUID("UUID"));if(actor==null||!found.add(actor.id()))throw new IllegalStateException("Actor record identity is unrelated or duplicated");var result=NativeRampActorRecord.classify(row,actor);if(result.state()==NativeRampActorRecord.State.REFUSED)throw new IllegalStateException("Actor physical record retained: "+result.reason());if(result.state()==NativeRampActorRecord.State.TRANSFERRED){if(changed==null)changed=chunk.copy();changed.getList("Entities",Tag.TAG_COMPOUND).set(i,NativeRampActorRecord.returned(row,actor));}}
  expected=raw;replacement=changed;
 }
 private static ChunkPos chunk(net.minecraft.world.phys.Vec3 point){return new ChunkPos(net.minecraft.util.Mth.floor(point.x)>>4,net.minecraft.util.Mth.floor(point.z)>>4);}
 @Override public void close(){if(prepared!=null){prepared.close();prepared=null;}if(permit!=null){permit.close();permit=null;}reading=null;completion=null;expected=null;replacement=null;}
}
