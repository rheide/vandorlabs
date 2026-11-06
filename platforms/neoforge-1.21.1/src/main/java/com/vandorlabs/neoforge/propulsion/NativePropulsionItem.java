package com.vandorlabs.neoforge.propulsion;

import com.vandorlabs.neoforge.placement.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Released generic engine-item merge semantics with a guarded stationary ownership transaction. */
public class NativePropulsionItem extends BlockItem {
 public static final String CONFIGURATION="BlockEntityTag",RECOVERY="PropulsionRecoverySnapshot",PLACEMENT_RECOVERY="PropulsionPlacementRecovery";
 public NativePropulsionItem(NativePropulsionBlock block,Properties properties){super(block,properties);}
 public static boolean mutating(Level world,BlockPos pos){return NativeStationaryPlacement.mutating(world,pos);}
 public static CompoundTag configuration(ItemStack stack){var nativeData=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(nativeData!=null)return nativeData.copyTag();var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom==null)return null;var root=custom.copyTag();if(root.contains(CONFIGURATION)&&!root.contains(CONFIGURATION,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Malformed propulsion item configuration");return root.contains(CONFIGURATION,Tag.TAG_COMPOUND)?root.getCompound(CONFIGURATION).copy():null;}
 private static ItemStack configured(Item item,CompoundTag config){var stack=new ItemStack(item);if(!config.isEmpty()){var root=new CompoundTag();root.put(CONFIGURATION,config.copy());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));}return stack;}
 public static ItemStack pick(NativePropulsionBlock block,NativePropulsionEntity owner){if(owner!=null&&owner.inert()){var config=new CompoundTag();config.put(RECOVERY,owner.recoverySnapshot());return configured(block.familyItem(),config);}return configured(block.familyItem(),owner==null?NativePropulsionPlacement.drop(block.definition()):NativePropulsionPlacement.pick(block.definition(),owner.settings()));}
 public static ItemStack drop(NativePropulsionBlock block,NativePropulsionEntity owner){return owner!=null&&owner.inert()?pick(block,owner):configured(block.familyItem(),NativePropulsionPlacement.drop(block.definition()));}
 protected boolean stage(Level world,BlockPos pos,BlockState state){return world.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
 protected boolean restore(Level world,BlockPos pos,BlockState state){return world.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
 private static NativePropulsionEntity recover(Level world,BlockPos pos,BlockState state,NativePropulsionEntity.Prepared prepared){if(world.getBlockState(pos)!=state)return null;var owner=world.getBlockEntity(pos) instanceof NativePropulsionEntity entity?entity:null;if(owner==null){owner=new NativePropulsionEntity(((NativePropulsionBlock)state.getBlock()).entityType(),pos,state);world.setBlockEntity(owner);if(world.getBlockEntity(pos)!=owner)return null;}owner.retainPlacementRecovery(prepared);owner.setChanged();return owner;}
 @Override public InteractionResult place(BlockPlaceContext context){var world=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var pos=context.getClickedPos();var source=(NativePropulsionBlock)getBlock();if(player==null||stack.isEmpty()||!context.canPlace()||!world.hasChunkAt(pos)||world.isOutsideBuildHeight(pos)||!world.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack)||world instanceof ServerLevel server&&!NativeRedstoneChannels.open(server))return InteractionResult.FAIL;var old=world.getBlockState(pos);if(old.hasBlockEntity()||world.getBlockEntity(pos)!=null||!old.canBeReplaced(context))return InteractionResult.FAIL;
  final BlockState state;final NativePropulsionEntity.Prepared prepared;try{var raw=configuration(stack);if(raw!=null&&(raw.contains(RECOVERY)||raw.contains(PLACEMENT_RECOVERY)))return InteractionResult.FAIL;var definition=NativePropulsionPlacement.select(source.definition().id,raw,context.getClickedFace(),NativePlacementHit.hit(context));var target=source.variant(definition.id);if(target==null)return InteractionResult.FAIL;state=target.defaultBlockState().setValue(NativePropulsionBlock.FACING,context.getClickedFace());if(!canPlace(context,state))return InteractionResult.FAIL;
   // Source ItemBlock applies NBT only if selected state block is the original item's block.
   prepared=NativePropulsionEntity.prepareItem(target,pos,state,target==source?raw:null,world.registryAccess());
  }catch(IllegalArgumentException invalid){return InteractionResult.FAIL;}if(world.isClientSide)return InteractionResult.SUCCESS;var placed=stack.copy();boolean committed=NativeStationaryPlacement.commit(world,pos,old,state,player,stack,new NativeStationaryPlacement.Hooks<NativePropulsionEntity>(){public boolean stage(){return NativePropulsionItem.this.stage(world,pos,state);}public boolean restore(){return NativePropulsionItem.this.restore(world,pos,old);}public NativePropulsionEntity owner(){return world.getBlockEntity(pos) instanceof NativePropulsionEntity owner?owner:null;}public void install(NativePropulsionEntity owner){owner.installItem(prepared);}public NativePropulsionEntity retainRecovery(){return recover(world,pos,state,prepared);}public String label(){return "Propulsion";}});if(!committed)return InteractionResult.FAIL;NativePropulsionService.flush(world);world.markAndNotifyBlock(pos,world.getChunkAt(pos),old,state,Block.UPDATE_ALL,512);state.getBlock().setPlacedBy(world,pos,state,player,placed);var sound=state.getSoundType(world,pos,player);world.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);world.gameEvent(player,GameEvent.BLOCK_PLACE,pos);if(player instanceof ServerPlayer server)net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(server,pos,placed);return InteractionResult.CONSUME;
 }
}
