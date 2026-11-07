package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.neoforge.placement.NativeStationaryPlacement;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Dedicated outer glass settings remain distinct from vanilla's generic block-entity merge. */
public class NativeGlassItem extends BlockItem {
 public static final String CONFIGURATION="ProgrammableGlassSettings",GENERIC="BlockEntityTag";
 public NativeGlassItem(NativeGlassBlock block,Properties properties){super(block,properties);}
 public static CompoundTag configuration(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return null;var root=data.copyTag();return root.contains(CONFIGURATION,Tag.TAG_COMPOUND)?root.getCompound(CONFIGURATION).copy():null;}
 public static CompoundTag genericConfiguration(ItemStack stack){var nativeData=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(nativeData!=null)return nativeData.copyTag();var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom==null)return null;var root=custom.copyTag();return root.contains(GENERIC,Tag.TAG_COMPOUND)?root.getCompound(GENERIC).copy():null;}
 public static String sourceModel(ItemStack stack){var tag=configuration(stack);int size=tag==null?1:Math.max(0,Math.min(1,tag.getInt("Size"))),shade=tag==null?0:com.vandorlabs.tiles.GlassTextures.clamp(tag.getInt("Shade")),tint=tag==null||!tag.contains("Tint",Tag.TAG_INT)?1:com.vandorlabs.tiles.GlassTextures.clampTint(tag.getInt("Tint"));return NativeFlatGlassAssets.item(size,shade,tint,tag==null||!tag.contains("Framed")||tag.getBoolean("Framed"));}
 private static ItemStack configured(Item item,CompoundTag choices){var stack=new ItemStack(item);var root=new CompoundTag();root.put(CONFIGURATION,choices.copy());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return stack;}
 public static ItemStack pick(NativeGlassBlock block,NativeGlassEntity owner,BlockState state){var tag=new CompoundTag();if(owner!=null&&owner.inert()){tag.put(NativeGlassEntity.RECOVERY,owner.recoverySnapshot());return configured(block.asItem(),tag);}var settings=owner==null?NativeGlassSettings.read(new CompoundTag()).resolveLegacySize(state.getValue(NativeGlassBlock.SIZE)):owner.settings();var g=settings.glass();tag.putInt("Size",owner==null?state.getValue(NativeGlassBlock.SIZE):g.size);tag.putInt("Shade",g.shade);tag.putBoolean("Join",g.join);tag.putInt("Tint",g.tint);tag.putInt("Material",g.material);tag.putBoolean("Stretch",g.stretch);tag.putInt("LayoutMode",g.textureLayout());tag.putBoolean("Framed",g.framed);settings.frames().write(new ModernHousingTags(tag));tag.putByte("GlassPlaybackMode",(byte)settings.playback().mode());tag.putByte("GlassPlaybackSpeed",(byte)settings.playback().speed());return configured(block.asItem(),tag);}
 public static ItemStack drop(NativeGlassBlock block,NativeGlassEntity owner,BlockState state){return owner!=null&&owner.inert()?pick(block,owner,state):new ItemStack(block);}
 public static boolean mutating(Level level,BlockPos pos){return NativeStationaryPlacement.mutating(level,pos);}
 protected boolean stage(Level world,BlockPos pos,BlockState state){return world.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
 protected boolean restore(Level world,BlockPos pos,BlockState old){return world.setBlock(pos,old,Block.UPDATE_KNOWN_SHAPE);}
 private static NativeGlassEntity recover(Level world,BlockPos pos,NativeGlassBlock block,NativeGlassEntity.Prepared prepared){if(world.getBlockState(pos)!=prepared.state)return null;var owner=world.getBlockEntity(pos) instanceof NativeGlassEntity host?host:null;if(owner==null){owner=new NativeGlassEntity(block.entityType(),pos,prepared.state);world.setBlockEntity(owner);if(world.getBlockEntity(pos)!=owner)return null;}owner.retainPlacementRecovery(prepared);owner.setChanged();return owner;}
 @Override public InteractionResult place(BlockPlaceContext context){var world=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var pos=context.getClickedPos();var block=(NativeGlassBlock)getBlock();if(player==null||stack.isEmpty()||!context.canPlace()||!NativeFluidWorld.loaded(world,pos)||world.isOutsideBuildHeight(pos)||!world.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack)||!NativeFluidWorld.open(world))return InteractionResult.FAIL;var old=world.getBlockState(pos);if(old.hasBlockEntity()||world.getBlockEntity(pos)!=null||!old.canBeReplaced(context))return InteractionResult.FAIL;
  final NativeGlassEntity.Prepared prepared;try{var state=block.getStateForPlacement(context);if(state==null||!canPlace(context,state))return InteractionResult.FAIL;prepared=NativeGlassEntity.prepareItem(block,pos,state,genericConfiguration(stack),configuration(stack),player.getDirection().getOpposite().get2DDataValue(),world.registryAccess());if(!canPlace(context,prepared.state))return InteractionResult.FAIL;}catch(IllegalArgumentException invalid){return InteractionResult.FAIL;}if(world.isClientSide)return InteractionResult.SUCCESS;var placed=stack.copy();boolean committed=NativeStationaryPlacement.commit(world,pos,old,prepared.state,player,stack,new NativeStationaryPlacement.Hooks<NativeGlassEntity>(){public boolean stage(){return NativeGlassItem.this.stage(world,pos,prepared.state);}public boolean restore(){return NativeGlassItem.this.restore(world,pos,old);}public NativeGlassEntity owner(){return world.getBlockEntity(pos) instanceof NativeGlassEntity host?host:null;}public void install(NativeGlassEntity owner){owner.installItem(prepared);owner.capturePlacementFluids(old);}public NativeGlassEntity retainRecovery(){return recover(world,pos,block,prepared);}public String label(){return "Glass";}});if(!committed)return InteractionResult.FAIL;var owner=(NativeGlassEntity)world.getBlockEntity(pos);NativeFluidWorld.changed(owner);world.markAndNotifyBlock(pos,world.getChunkAt(pos),old,prepared.state,Block.UPDATE_ALL,512);prepared.state.getBlock().setPlacedBy(world,pos,prepared.state,player,placed);var sound=prepared.state.getSoundType(world,pos,player);world.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);world.gameEvent(player,GameEvent.BLOCK_PLACE,pos);if(player instanceof net.minecraft.server.level.ServerPlayer server)net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(server,pos,placed);return InteractionResult.CONSUME;
 }
}
