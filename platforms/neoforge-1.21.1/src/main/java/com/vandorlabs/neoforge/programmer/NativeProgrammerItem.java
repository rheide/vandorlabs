package com.vandorlabs.neoforge.programmer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
/** Plain four-cell placement. Source item tile data never installs a second physical inventory. */
public class NativeProgrammerItem extends BlockItem {
 private record Scope(Level level,BlockPos[] cells){}
 private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
 public NativeProgrammerItem(NativeProgrammerBlock block,Properties properties){super(block,properties);}
 static boolean mutating(Level level,BlockPos pos){var scope=ACTIVE.get();if(scope==null||scope.level()!=level)return false;for(var cell:scope.cells())if(cell.equals(pos))return true;return false;}
 protected boolean stage(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
 protected boolean restore(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
 @Override public InteractionResult place(BlockPlaceContext context){
  var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var block=(NativeProgrammerBlock)getBlock();var root=context.getClickedPos();
  if(player==null||stack.isEmpty()||!context.canPlace()||level instanceof ServerLevel server&&!com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))return InteractionResult.FAIL;
  BlockState base=block.getStateForPlacement(context);if(base==null)return InteractionResult.FAIL;
  BlockPos[] cells=new BlockPos[4];BlockState[] previous=new BlockState[4],states=new BlockState[4];
  for(int part=0;part<4;part++){var pos=NativeProgrammerBlock.cell(root,base.getValue(NativeProgrammerBlock.FACING),part);cells[part]=pos;states[part]=base.setValue(NativeProgrammerBlock.PART,part);if(!level.hasChunkAt(pos)||pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(pos)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack))return InteractionResult.FAIL;var old=level.getBlockState(pos);previous[part]=old;if(old.hasBlockEntity()||level.getBlockEntity(pos)!=null||!old.canBeReplaced(context))return InteractionResult.FAIL;}
  if(!canPlace(context,base))return InteractionResult.FAIL;if(level.isClientSide)return InteractionResult.SUCCESS;
  ItemStack placed=stack.copy();Scope prior=ACTIVE.get();ACTIVE.set(new Scope(level,cells));boolean committed=false;Throwable original=null;NativeProgrammerEntity owner=null;
  try{for(int part=0;part<4;part++){if(!stage(level,cells[part],states[part])||level.getBlockState(cells[part])!=states[part])return InteractionResult.FAIL;if(part==0){if(!(level.getBlockEntity(root) instanceof NativeProgrammerEntity installed)||installed.inert())return InteractionResult.FAIL;owner=installed;}else if(level.getBlockEntity(cells[part])!=null)return InteractionResult.FAIL;}for(int part=0;part<4;part++)if(level.getBlockState(cells[part])!=states[part])return InteractionResult.FAIL;if(level.getBlockEntity(root)!=owner)return InteractionResult.FAIL;if(!player.getAbilities().instabuild)stack.shrink(1);committed=true;
  }catch(RuntimeException|Error failure){original=failure;throw failure;}finally{Throwable rollback=null;try{if(!committed){for(int part=3;part>=0;part--)try{if(level.getBlockState(cells[part])==states[part]&&!restore(level,cells[part],previous[part])&&level.getBlockState(cells[part])!=previous[part])throw new IllegalStateException("Programmer rollback refused at "+cells[part]);}catch(RuntimeException|Error failure){if(rollback==null)rollback=failure;else rollback.addSuppressed(failure);}if(level.getBlockState(root)==states[0]){var installed=level.getBlockEntity(root);if(installed instanceof NativeProgrammerEntity survivor&&(owner==null||owner==survivor)){var recovery=survivor.saveWithoutMetadata(level.registryAccess());recovery.putBoolean("ProgrammerPlacementRecovery",true);survivor.loadWithComponents(recovery,level.registryAccess());survivor.setChanged();}else if(installed==null&&!player.getAbilities().instabuild){/* A bare surviving root remains the one plain block item even if its factory recovers later. */stack.shrink(1);}}}}finally{if(prior==null)ACTIVE.remove();else ACTIVE.set(prior);}if(rollback!=null){if(original!=null)original.addSuppressed(rollback);else throw new IllegalStateException("Programmer rollback failed; surviving root ownership retained",rollback);}}
  // The complete owner and all parts exist, and the source item was consumed, before observers run.
  for(int part=0;part<4;part++)if(level.getBlockState(cells[part])==states[part])level.markAndNotifyBlock(cells[part],level.getChunkAt(cells[part]),previous[part],states[part],Block.UPDATE_ALL,512);
  if(level.getBlockEntity(root)==owner)block.setPlacedBy(level,root,base,player,placed);
  var sound=base.getSoundType(level,root,player);level.playSound(null,root,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);level.gameEvent(player,GameEvent.BLOCK_PLACE,root);if(player instanceof ServerPlayer server)net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(server,root,placed);return InteractionResult.CONSUME;
 }
}
