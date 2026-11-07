package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.NativeVoxelShapes;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Inactive generated-part host. All collision reads consume one immutable prepared value. */
public class NativeRampPartEntity extends NativeRampJournalPart {
 public record Publication(NativeRampPartData data,NativeRampGeometry.Snapshot geometry,
   NativeRampBeltGeometry.Snapshot beltGeometry,double pose,VoxelShape collisionShape,List<AABB> worldBoxes,long revision,long observationTick,boolean stationaryTop){
  public Publication{worldBoxes=List.copyOf(worldBoxes);}
 }
 private static final int SHAPE_BUDGET=4*1024*1024;
 private volatile Publication publication;
 public NativeRampPartEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);getPersistentData();}
 /** Build a complete detached value; no world access, ownership change or notifications. */
 final Publication preparePublication(NativeRampPartData data,long revision,long tick){if(level!=null)throw new IllegalStateException("Ramp staging requires a detached part");if(getBlockState().hasProperty(NativeRampPartBlock.FACING)&&getBlockState().getValue(NativeRampPartBlock.FACING)!=data.spec().facing())throw new IllegalArgumentException("Ramp staged facing");return build(data,data.motion().observed(),data.belt().observed(),revision,tick);}
 /** Initialize a detached factory result before the caller installs it in the world. */
 final void stagePublication(UUID operation,BlockPos root,Publication prepared){
  if(level!=null||isRemoved()||ownershipKnown()||publication!=null||!NativeRampPartEnvelope.live(this))throw new IllegalStateException("Ramp part already initialized");Objects.requireNonNull(prepared);if(!prepared.data.spec().cell().equals(worldPosition)||root==null||Math.abs((long)root.getX()-worldPosition.getX())>64||Math.abs((long)root.getY()-worldPosition.getY())>64||Math.abs((long)root.getZ()-worldPosition.getZ())>64)throw new IllegalArgumentException("Ramp staged owner/cell");install(operation,root);publication=prepared;
 }
 @Override public final boolean ownershipKnown(){return super.ownershipKnown()&&NativeRampPartEnvelope.live(this);}
 public final Publication publication(){return publication;}
 /** Ordering is per operation: a fresh operation deliberately starts a new cosmetic identity. */
 public final boolean acceptsObservation(UUID operation,long revision,long tick){var previous=publication;return revision>=0&&(previous==null||!Objects.equals(operation,operationId())||revision>previous.revision||revision==previous.revision&&tick>=previous.observationTick);}
 public final NativeRampPartData data(){var value=publication;return value==null?null:value.data;}
 private Publication build(NativeRampPartData data,double pose,double distance,long revision,long observationTick){return build(data,pose,distance,revision,observationTick,null);}
 private Publication build(NativeRampPartData data,double pose,double distance,long revision,long observationTick,Boolean stationaryTop){
  if(revision<0)throw new IllegalArgumentException("Ramp publication revision");
  if(!data.spec().cell().equals(worldPosition))throw new IllegalArgumentException("Ramp part cell identity");
  var belt=com.vandorlabs.ramp.RampBeltPolicy.deployed(data.belt().mode(),data.motion().moving(),pose)?NativeRampBeltGeometry.snapshot(data.spec(),distance):null;
  var geometry=belt==null?NativeRampGeometry.snapshot(data.spec(),pose):belt.geometry();
  var local=new ArrayList<AABB>(geometry.pieces().size());var world=new ArrayList<AABB>(geometry.pieces().size());
  for(var piece:geometry.pieces()){var box=piece.box();if(box.minX<0||box.minY<0||box.minZ<0||box.maxX>1||box.maxY>1||box.maxZ>1)throw new IllegalArgumentException("Ramp part escaped reserved cell");local.add(box);world.add(box.move(worldPosition));}
  var shape=NativeVoxelShapes.union(local,SHAPE_BUDGET,1e-8);
  if((belt==null?geometry.retainedBytes():belt.retainedBytes())+NativeVoxelShapes.retainedCost(shape,local.size())>SHAPE_BUDGET)throw new IllegalArgumentException("Ramp part publication budget");
  return new Publication(data,geometry,belt,pose,shape,world,revision,observationTick,stationaryTop==null?NativeRampPartSupport.stationaryTop(data.spec(),data.belt().mode()):stationaryTop);
 }
 private boolean installed(){if(level==null||isRemoved())return false;if(level instanceof ServerLevel server&&!server.getServer().isSameThread())return false;var chunk=level.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(worldPosition)==this;}
 /** Called within the journal's exact-operation publication scope, before observers run. */
 final void installPart(UUID operation,BlockPos root,NativeRampPartData data){var previous=publication;installPart(operation,root,data,previous!=null&&belongs(operation,root)?Math.addExact(previous.revision,1):0);}
 final void installPart(UUID operation,BlockPos root,NativeRampPartData data,long revision){
  if(!installed()||level.isClientSide)throw new IllegalStateException("Ramp part installation thread/identity");
  if(root==null||Math.abs((long)root.getX()-worldPosition.getX())>64||Math.abs((long)root.getY()-worldPosition.getY())>64||Math.abs((long)root.getZ()-worldPosition.getZ())>64)throw new IllegalArgumentException("Ramp owner bounds");
  var previous=publication;if(previous!=null&&belongs(operation,root)&&revision<=previous.revision)throw new IllegalArgumentException("Ramp control revision must advance");
  var next=build(data,data.motion().observed(),data.belt().observed(),revision,level.getGameTime());
  install(operation,root);publication=next;setChanged();
 }
 /** The controller owns scheduling and durable changing-cell admission; this method does neither. */
 public final void prepare(long tick){
  if(!installed()||level.isClientSide||!ownershipKnown())return;var previous=publication;if(previous==null)return;
  var data=previous.data;double pose=data.motion().pose(tick,0),distance=data.belt().distance();
  if(data.belt().running())distance+=((double)tick-data.belt().tick())*data.belt().velocity(data.spec());
  if(pose==previous.pose&&(previous.beltGeometry==null||distance==previous.beltGeometry.distance()))return;
  publication=build(data,pose,distance,previous.revision,tick,previous.stationaryTop);
 }
 @Override public List<AABB> physicalBounds(){var value=publication;return ownershipKnown()&&value!=null?value.worldBoxes:null;}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
  var original=tag.copy();try{
   long revision=number(tag,"NativeRampPublicationRevision"),observationTick=number(tag,"NativeRampObservationTick");var previous=publication;
   if(level!=null&&level.isClientSide&&previous!=null&&tag.hasUUID("Operation")&&!acceptsObservation(tag.getUUID("Operation"),revision,observationTick))return;
   NativeRampPartEnvelope.require(tag);
   if(!tag.contains("NativeRampPartData",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing Ramp part body");
   var data=NativeRampPartData.read(tag.getCompound("NativeRampPartData"),worldPosition,registries);
   var next=build(data,data.motion().observed(),data.belt().observed(),revision,observationTick);
   super.loadAdditional(tag,registries);if(!super.ownershipKnown())throw new IllegalArgumentException("Unknown Ramp part owner");publication=next;
  }catch(RuntimeException failure){publication=null;refuseSnapshot(original);}
 }
 private static long number(CompoundTag tag,String key){if(!tag.contains(key))return 0;if(!tag.contains(key,Tag.TAG_LONG))throw new IllegalArgumentException("Ramp observation field type");return tag.getLong(key);}
 private static NativeRampPartData observed(Publication value,long tick){var data=value.data.observed(tick);var belt=data.belt();if(belt.mode()!=0){double distance=belt.distance()+(belt.running()?((double)tick-belt.tick())*belt.velocity(data.spec()):0);data=data.withBelt(new NativeRampPartData.Belt(belt.mode(),belt.speed(),belt.distance(),belt.tick(),belt.running(),distance));}return data;}
 private static void writeProjection(CompoundTag tag,Publication value,long tick){tag.putLong("NativeRampPublicationRevision",value.revision);tag.putLong("NativeRampObservationTick",tick);tag.put("NativeRampPartData",observed(value,tick).write());}
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);var value=publication;if(value!=null)writeProjection(tag,value,value.observationTick);}
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);var value=publication;if(ownershipKnown()&&value!=null)writeProjection(tag,value,level==null?value.observationTick:level.getGameTime());return tag;}
 private boolean staleUpdate(CompoundTag tag){if(!tag.hasUUID("Operation"))return false;try{return !acceptsObservation(tag.getUUID("Operation"),number(tag,"NativeRampPublicationRevision"),number(tag,"NativeRampObservationTick"));}catch(IllegalArgumentException malformed){return false;}}
 // Vanilla installs components after loadAdditional, so admission must wrap the whole load.
 @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){var tag=packet.getTag();if(!tag.isEmpty()&&!staleUpdate(tag))loadWithComponents(tag,registries);}
 @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){if(!staleUpdate(tag))loadWithComponents(tag,registries);}
 @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
