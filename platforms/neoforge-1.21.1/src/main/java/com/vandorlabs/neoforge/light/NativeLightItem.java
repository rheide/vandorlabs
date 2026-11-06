package com.vandorlabs.neoforge.light;
import com.vandorlabs.neoforge.placement.NativeStationaryPlacement;
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
/** Source configured choices, prepared before mutation; opaque failed ownership remains an unplaceable item. */
public class NativeLightItem extends BlockItem {
 public static final String CONFIGURATION="BlockEntityTag",RECOVERY="LightRecoverySnapshot",PLACEMENT_RECOVERY="LightPlacementRecovery";
 public NativeLightItem(NativeLightBlock block,Properties properties){super(block,properties);}
 public static boolean mutating(Level world,BlockPos pos){return NativeStationaryPlacement.mutating(world,pos);}
 public static CompoundTag configuration(ItemStack stack){var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(data!=null)return data.copyTag();var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom==null)return null;var root=custom.copyTag();if(root.contains(CONFIGURATION)&&!root.contains(CONFIGURATION,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Malformed Light item configuration");return root.contains(CONFIGURATION,Tag.TAG_COMPOUND)?root.getCompound(CONFIGURATION).copy():null;}
 public static ItemStack pick(Item item,NativeLightEntity owner){var stack=new ItemStack(item);if(owner!=null){var config=owner.itemSettings();if(owner.recoveryRequired()){config=new CompoundTag();config.put(RECOVERY,owner.recoverySnapshot());}var root=new CompoundTag();root.put(CONFIGURATION,config);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));}return stack;}
 protected boolean stage(Level world,BlockPos pos,BlockState state){return world.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
 protected boolean restore(Level world,BlockPos pos,BlockState old){return world.setBlock(pos,old,Block.UPDATE_KNOWN_SHAPE);}
 private static NativeLightEntity recovery(Level world,BlockPos pos,BlockState state,NativeLightBlock block,NativeLightEntity.Prepared choices){if(world.getBlockState(pos)!=state)return null;var owner=world.getBlockEntity(pos) instanceof NativeLightEntity tile?tile:null;if(owner==null){owner=new NativeLightEntity(block.entityType(),pos,state,com.vandorlabs.neoforge.screen.NativeScreenCatalog.INSTANCE);world.setBlockEntity(owner);if(world.getBlockEntity(pos)!=owner)return null;}owner.retainPlacementRecovery(choices);owner.setChanged();return owner;}
 @Override public InteractionResult place(BlockPlaceContext context){var world=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var pos=context.getClickedPos();var block=(NativeLightBlock)getBlock();if(player==null||stack.isEmpty()||!context.canPlace()||!world.hasChunkAt(pos)||world.isOutsideBuildHeight(pos)||!world.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack))return InteractionResult.FAIL;if(world instanceof ServerLevel server&&!NativeRedstoneChannels.open(server))return InteractionResult.FAIL;var old=world.getBlockState(pos);if(old.hasBlockEntity()||world.getBlockEntity(pos)!=null||!old.canBeReplaced(context))return InteractionResult.FAIL;var state=block.getStateForPlacement(context);if(state==null||!canPlace(context,state))return InteractionResult.FAIL;
  final NativeLightEntity.Prepared choices;try{choices=NativeLightEntity.prepareItem(block.entityType(),pos,state,configuration(stack),world.registryAccess());}catch(IllegalArgumentException invalid){return InteractionResult.FAIL;}if(world.isClientSide)return InteractionResult.SUCCESS;var placed=stack.copy();boolean committed=NativeStationaryPlacement.commit(world,pos,old,state,player,stack,new NativeStationaryPlacement.Hooks<NativeLightEntity>(){public boolean stage(){return NativeLightItem.this.stage(world,pos,state);}public boolean restore(){return NativeLightItem.this.restore(world,pos,old);}public NativeLightEntity owner(){return world.getBlockEntity(pos) instanceof NativeLightEntity owner?owner:null;}public void install(NativeLightEntity owner){owner.installItem(choices);}public NativeLightEntity retainRecovery(){return recovery(world,pos,state,block,choices);}public String label(){return "Light";}});if(!committed)return InteractionResult.FAIL;
  NativeLightService.flush((ServerLevel)world);world.markAndNotifyBlock(pos,world.getChunkAt(pos),old,state,Block.UPDATE_ALL,512);block.setPlacedBy(world,pos,state,player,placed);var sound=state.getSoundType(world,pos,player);world.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);world.gameEvent(player,GameEvent.BLOCK_PLACE,pos);if(player instanceof ServerPlayer server)net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(server,pos,placed);return InteractionResult.CONSUME;
 }
}
