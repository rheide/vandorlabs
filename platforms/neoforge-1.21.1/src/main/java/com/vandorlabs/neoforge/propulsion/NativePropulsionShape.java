package com.vandorlabs.neoforge.propulsion;
import com.vandorlabs.propulsion.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Source square/hex/wedge conversion retains each physical settings owner across native block replacement. */
public final class NativePropulsionShape {
 private record Scope(Level world,Set<BlockPos> cells){}
 private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
 private NativePropulsionShape(){}
 static boolean mutating(Level world,BlockPos pos){var scope=ACTIVE.get();return scope!=null&&scope.world==world&&scope.cells.contains(pos);}
 interface Writer {boolean write(ServerLevel world,BlockPos pos,BlockState state);default void phase(String phase,BlockPos pos){}}
 private static final Writer WORLD=(world,pos,state)->world.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);
 public static boolean configure(NativePropulsionEntity selected,int shape,Player actor){return configure(selected,shape,actor,WORLD);}
 static boolean configure(NativePropulsionEntity selected,int shape,Player actor,Writer writer){
  if(!(selected.getLevel() instanceof ServerLevel world)||!NativeRedstoneChannels.open(world)||!NativePropulsionService.installed(selected)||!selected.available()||shape<0||shape>2)return false;
  var source=(NativePropulsionBlock)selected.getBlockState().getBlock();var definition=source.definition();if(definition.family.isEmpty()||definition.shape==shape)return true;
  int face=selected.getBlockState().getValue(NativePropulsionBlock.FACING).get3DDataValue();var targetDefinition=PropulsionPlacement.select(PropulsionDefinition.of(definition.family),shape,face,.25F,.25F,.25F);var target=source.variant(targetDefinition.id);if(target==null)return false;
  NativePropulsionService.refreshProjection(selected);var members=shape!=2&&definition.maxConnected>0?NativePropulsionService.members(selected):List.of(selected);if(members.isEmpty())return false;
  Map<BlockPos,NativePropulsionEntity> owners=new LinkedHashMap<>();Map<BlockPos,BlockState> before=new LinkedHashMap<>();Map<BlockPos,net.minecraft.nbt.CompoundTag> saved=new LinkedHashMap<>();
  for(var owner:members){var pos=owner.getBlockPos();if(!NativePropulsionService.installed(owner)||!owner.available()||owner.getBlockState().getBlock()!=source)return false;if(actor!=null&&(actor.level()!=world||!actor.isAlive()||actor.isSpectator()||!world.mayInteract(actor,pos)||!actor.mayUseItemAt(pos,net.minecraft.core.Direction.UP,actor.getMainHandItem())))return false;owners.put(pos,owner);before.put(pos,owner.getBlockState());saved.put(pos,owner.saveWithoutMetadata(world.registryAccess()));}
  Scope previous=ACTIVE.get();ACTIVE.set(new Scope(world,Set.copyOf(owners.keySet())));List<BlockPos> touched=new ArrayList<>();boolean success=false;
  try{for(var entry:before.entrySet()){var pos=entry.getKey();touched.add(pos);writer.phase("before-write",pos);var old=entry.getValue();var after=target.defaultBlockState().setValue(NativePropulsionBlock.FACING,old.getValue(NativePropulsionBlock.FACING)).setValue(NativePropulsionBlock.POWERED,old.getValue(NativePropulsionBlock.POWERED));if(!writer.write(world,pos,after)&&world.getBlockState(pos)!=after)throw new IllegalStateException("Rejected propulsion shape replacement");writer.phase("after-write",pos);reattach(world,pos,owners.get(pos));}success=true;
  }catch(RuntimeException failure){for(int i=touched.size()-1;i>=0;i--){var pos=touched.get(i);var old=before.get(pos);try{if(!writer.write(world,pos,old)&&world.getBlockState(pos)!=old)throw new IllegalStateException("Rejected propulsion shape rollback");reattach(world,pos,owners.get(pos));}catch(RuntimeException rollback){if(world.getBlockState(pos).getBlock() instanceof NativePropulsionBlock){reattach(world,pos,owners.get(pos));owners.get(pos).retainShapeRecovery(saved.get(pos),source.definition().id);}rollback.addSuppressed(failure);throw new IllegalStateException("Propulsion shape rollback failed; owner retained",rollback);}}}
  finally{if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);for(var owner:owners.values()){NativePropulsionService.queue(owner);owner.setChanged();var state=world.getBlockState(owner.getBlockPos());world.sendBlockUpdated(owner.getBlockPos(),before.get(owner.getBlockPos()),state,Block.UPDATE_CLIENTS);}}
  return success;
 }
 private static void reattach(ServerLevel world,BlockPos pos,NativePropulsionEntity owner){var state=world.getBlockState(pos);if(!(state.getBlock() instanceof NativePropulsionBlock))throw new IllegalStateException("Propulsion owner cell replaced by a foreign block");if(world.getBlockEntity(pos)!=owner)world.removeBlockEntity(pos);owner.reattach(state);world.setBlockEntity(owner);if(world.getBlockEntity(pos)!=owner)throw new IllegalStateException("Propulsion owner reattachment refused");}
}
