package com.vandorlabs.neoforge.ramp;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Startup-only adoption of one durable ACTIVE scope. It rewrites only authenticated
 * same-operation proxy geometry; resource before-images remain private in the journal. */
final class NativeRampHealthyAdoption implements AutoCloseable {
 private final ServerLevel level;private final NativeRampJournal.Operation operation;
 private CompletableFuture<Optional<CompoundTag>> reading;private boolean done,actorsReady;private NativeRampHealthyActors actors;
 NativeRampHealthyAdoption(ServerLevel level,NativeRampJournal.Operation operation){this.level=level;this.operation=operation;if(!NativeRampRecoveryAdmission.active(level)||!NativeRampJournal.startupActiveReceipt(operation)||NativeRampJournal.retainedActiveActors(operation))throw new IllegalStateException("Healthy Ramp adoption requires its durable startup receipt");}
 NativeRampJournal.Status poll(){
  NativeRampRiders.requireThread(level);if(done)return NativeRampJournal.Status.READY;
  if(reading==null){reading=level.getChunkSource().chunkMap.read(new ChunkPos(operation.root()));return NativeRampJournal.Status.PENDING;}
  if(!reading.isDone())return NativeRampJournal.Status.PENDING;
  var raw=reading.join().orElseThrow(()->new IllegalStateException("Saved ACTIVE Ramp root chunk is missing"));if(!raw.contains("DataVersion",Tag.TAG_INT)||raw.getInt("DataVersion")!=SharedConstants.getCurrentVersion().getDataVersion().getVersion())throw new IllegalStateException("Unproven ACTIVE root chunk data version retained");
  var saved=NativeRampSavedRoot.read(raw,operation.root());var identity=NativeRampJournal.rootIdentity(level,operation);var state=com.vandorlabs.neoforge.transfer.NativeRawCell.decodePalette(level.registryAccess(),saved.state(),Map.of());var tile=saved.tile();if(NativeRampRuntime.normalized(state)!=identity.state||!tile.hasUUID(NativeRampRuntime.OWNER)||!tile.getUUID(NativeRampRuntime.OWNER).equals(identity.owner)||!tile.getString("id").equals(identity.type.toString())||!tile.getCompound("NeoForgeData").hasUUID("VandorRampJournal")||!tile.getCompound("NeoForgeData").getUUID("VandorRampJournal").equals(operation.id()))throw new IllegalStateException("Saved ACTIVE Ramp root identity/marker changed");
  NativeRampRuntime runtime=tile.contains(NativeRampRuntime.KEY)?NativeRampRuntime.read(tile.getCompound(NativeRampRuntime.KEY),operation.root(),level.registryAccess()):NativeRampJournal.initialRuntime(level,operation);
  if(runtime.held||!runtime.owner.equals(identity.owner)||runtime.rootState!=identity.state||!runtime.type.equals(identity.type)||!runtime.frame.operation.equals(operation.id())||!Arrays.equals(runtime.frame.recipe,NativeRampJournal.recipeIdentity(operation)))throw new IllegalStateException("ACTIVE Ramp runtime identity or held state is not adoptable");
  var assembly=operation.assembly();var target=assembly.cells(runtime.frame.timeline.observed,runtime.frame.timeline.observed);if(!assembly.potential().containsAll(runtime.frame.cells)||!runtime.frame.cells.containsAll(target))throw new IllegalStateException("Saved ACTIVE reservation does not cover its committed geometry");
  if(!actorsReady){if(actors==null)actors=new NativeRampHealthyActors(level,operation,runtime);if(!actors.poll())return NativeRampJournal.Status.PENDING;actorsReady=true;actors=null;}if(NativeRampJournal.retireObsoleteActors(level,operation)!=NativeRampJournal.Status.READY)return NativeRampJournal.Status.PENDING;
  var chunks=new LinkedHashSet<ChunkPos>();chunks.add(new ChunkPos(operation.root()));for(var pos:assembly.potential())chunks.add(new ChunkPos(pos));if(chunks.size()>64)throw new IllegalStateException("ACTIVE Ramp chunk admission bound");for(var pos:chunks)level.getChunk(pos.x,pos.z);
  if(!(level.getBlockEntity(operation.root()) instanceof NativeRampEntity owner)||owner.inert())throw new IllegalStateException("ACTIVE Ramp root factory or snapshot is unavailable");identity.require(owner);if(!NativeRampJournal.sameRuntimeChoices(assembly.settings(),owner.settings()))throw new IllegalStateException("ACTIVE Ramp root choices changed");
  var actualSaved=owner.loadedRuntime();if((actualSaved==null)!=!tile.contains(NativeRampRuntime.KEY)||actualSaved!=null&&!actualSaved.write().equals(runtime.write()))throw new IllegalStateException("Root changed between raw admission and physical load");
  var retained=new LinkedHashMap<BlockPos,NativeRampPartEntity>();var old=new LinkedHashMap<BlockPos,NativeRampPartEntity>();long revision=runtime.frame.revision;
  for(var pos:assembly.potential()){
   var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)throw new IllegalStateException("ACTIVE Ramp chunk disappeared");var block=chunk.getBlockState(pos);var entity=chunk.getBlockEntities().get(pos);if(block.getBlock() instanceof NativeRampPartBlock&&entity==null)entity=level.getBlockEntity(pos);
   if(block.isAir()&&entity==null&&chunk.getBlockEntityNbt(pos)==null)continue;
   if(block.is(NativeRampJournal.frameProxy(operation).getBlock())&&entity instanceof NativeRampPartEntity part&&part.belongs(operation.id(),operation.root())&&part.publication()!=null){var data=part.data();if(!assembly.part(pos,data.motion(),data.belt()).write().equals(data.write()))throw new IllegalStateException("ACTIVE part recipe or material changed");old.put(pos,part);revision=Math.max(revision,part.publication().revision());if(target.contains(pos))retained.put(pos,part);continue;}
   if(block.getBlock() instanceof NativeRampPartBlock&&(entity==null||entity instanceof NativeRampPartEntity part&&!part.ownershipKnown()))throw new IllegalStateException("Unknown ACTIVE Ramp proxy snapshot retained");
   if(target.contains(pos))throw new IllegalStateException("Foreign block obstructs saved ACTIVE Ramp geometry");
  }
  revision=Math.addExact(revision,1);var t=runtime.frame.timeline;var motion=new NativeRampPartData.Motion(t.sequence,level.getGameTime(),t.observed,t.observed,t.duration,false,t.observed);var oldBelt=runtime.frame.belt;var belt=oldBelt.mode()==0?NativeRampPartData.Belt.NONE:new NativeRampPartData.Belt(oldBelt.mode(),oldBelt.speed(),oldBelt.observed(),level.getGameTime(),false,oldBelt.observed());var frame=new NativeRampFramePublisher.Frame(target,motion,belt,revision);
  NativeRampJournal.bindAdoptedOwner(level,operation,owner);var prepared=NativeRampPartPublisher.prepareRetaining(level,NativeRampJournal.frameProxy(operation),operation.id(),operation.root(),assembly,target,motion,belt,revision,64L*1024*1024,retained);
  try(var mutation=NativeRampHeldOwners.mutate(operation)){prepared.install();for(var entry:old.entrySet())if(!target.contains(entry.getKey())){if(level.getBlockEntity(entry.getKey())!=entry.getValue()||!entry.getValue().belongs(operation.id(),operation.root()))throw new IllegalStateException("ACTIVE retirement owner changed");if(!NativeRampHeldOwners.setBlock(level,entry.getKey(),Blocks.AIR.defaultBlockState(),Block.UPDATE_KNOWN_SHAPE))throw new IllegalStateException("ACTIVE retirement refused");}}
  owner.controller().adopt(operation,runtime,frame);for(var pos:assembly.potential())if(old.containsKey(pos)||target.contains(pos)){var current=level.getBlockState(pos);level.sendBlockUpdated(pos,NativeRampJournal.frameProxy(operation),current,Block.UPDATE_CLIENTS);}owner.runtimeChanged(true);done=true;return NativeRampJournal.Status.READY;
 }
 @Override public void close(){if(actors!=null){actors.close();actors=null;}}
}
