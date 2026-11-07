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
   NativeRampBeltGeometry.Snapshot beltGeometry,double pose,VoxelShape collisionShape,List<AABB> worldBoxes){
  public Publication{worldBoxes=List.copyOf(worldBoxes);}
 }
 private static final int SHAPE_BUDGET=4*1024*1024;
 private volatile Publication publication;
 public NativeRampPartEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
 public final Publication publication(){return publication;}
 public final NativeRampPartData data(){var value=publication;return value==null?null:value.data;}
 private Publication build(NativeRampPartData data,double pose,double distance){
  if(!data.spec().cell().equals(worldPosition))throw new IllegalArgumentException("Ramp part cell identity");
  var belt=data.belt().mode()==0?null:NativeRampBeltGeometry.snapshot(data.spec(),distance);
  var geometry=belt==null?NativeRampGeometry.snapshot(data.spec(),pose):belt.geometry();
  var local=new ArrayList<AABB>(geometry.pieces().size());var world=new ArrayList<AABB>(geometry.pieces().size());
  for(var piece:geometry.pieces()){var box=piece.box();if(box.minX<0||box.minY<0||box.minZ<0||box.maxX>1||box.maxY>1||box.maxZ>1)throw new IllegalArgumentException("Ramp part escaped reserved cell");local.add(box);world.add(box.move(worldPosition));}
  var shape=NativeVoxelShapes.union(local,SHAPE_BUDGET,1e-8);
  if((belt==null?geometry.retainedBytes():belt.retainedBytes())+NativeVoxelShapes.retainedCost(shape,local.size())>SHAPE_BUDGET)throw new IllegalArgumentException("Ramp part publication budget");
  return new Publication(data,geometry,belt,pose,shape,world);
 }
 private boolean installed(){if(level==null||isRemoved())return false;if(level instanceof ServerLevel server&&!server.getServer().isSameThread())return false;var chunk=level.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(worldPosition)==this;}
 /** Called within the journal's exact-operation publication scope, before observers run. */
 final void installPart(UUID operation,BlockPos root,NativeRampPartData data){
  if(!installed()||level.isClientSide)throw new IllegalStateException("Ramp part installation thread/identity");
  if(root==null||Math.abs((long)root.getX()-worldPosition.getX())>64||Math.abs((long)root.getY()-worldPosition.getY())>64||Math.abs((long)root.getZ()-worldPosition.getZ())>64)throw new IllegalArgumentException("Ramp owner bounds");
  var next=build(data,data.motion().observed(),data.belt().observed());
  install(operation,root);publication=next;setChanged();
 }
 /** The controller owns scheduling and durable changing-cell admission; this method does neither. */
 public final void prepare(long tick){
  if(!installed()||level.isClientSide||!ownershipKnown())return;var previous=publication;if(previous==null)return;
  var data=previous.data;double pose=data.motion().pose(tick,0),distance=data.belt().distance();
  if(data.belt().running())distance+=((double)tick-data.belt().tick())*data.belt().velocity(data.spec());
  if(pose==previous.pose&&(previous.beltGeometry==null||distance==previous.beltGeometry.distance()))return;
  publication=build(data,pose,distance);
 }
 @Override public List<AABB> physicalBounds(){var value=publication;return ownershipKnown()&&value!=null?value.worldBoxes:null;}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
  var original=tag.copy();try{
   if(!tag.contains("NativeRampPartData",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing Ramp part body");
   var data=NativeRampPartData.read(tag.getCompound("NativeRampPartData"),worldPosition,registries);
   var next=build(data,data.motion().observed(),data.belt().observed());
   super.loadAdditional(tag,registries);if(!ownershipKnown())throw new IllegalArgumentException("Unknown Ramp part owner");publication=next;
  }catch(RuntimeException failure){publication=null;refuseSnapshot(original);}
 }
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);var value=publication;if(ownershipKnown()&&value!=null)tag.put("NativeRampPartData",value.data.write());}
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);var value=publication;if(ownershipKnown()&&value!=null){var data=value.data;if(level!=null)data=data.observed(level.getGameTime());if(value.beltGeometry!=null){var belt=data.belt();data=data.withBelt(new NativeRampPartData.Belt(belt.mode(),belt.speed(),belt.distance(),belt.tick(),belt.running(),value.beltGeometry.distance()));}tag.put("NativeRampPartData",data.write());}return tag;}
 @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
