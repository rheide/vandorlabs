package com.vandorlabs.neoforge.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import java.util.function.Supplier;

/** Inactive native independent Storage. Dedicated material editor binding is supplied separately. */
public class NativeStorageBlock extends BaseEntityBlock {
 public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;private final Supplier<? extends BlockEntityType<?>> entities;
 public NativeStorageBlock(Properties properties,Supplier<? extends BlockEntityType<?>> entities){super(properties);this.entities=java.util.Objects.requireNonNull(entities);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return entities.get().create(pos,state);}
 public BlockState legacyState(int meta){var facing=Direction.from3DDataValue(meta&7);return defaultBlockState().setValue(FACING,facing.getAxis().isHorizontal()?facing:Direction.NORTH);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
 @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
 @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return 0;}
 @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
 @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){return level.getBlockEntity(pos) instanceof NativeStorageEntity owner?owner.comparator():0;}
 protected boolean openConfiguration(Player player,Level level,BlockPos pos){return false;}
 private InteractionResult inventory(Player player,Level level,BlockPos pos){if(level.isClientSide)return InteractionResult.SUCCESS;if(!(player instanceof ServerPlayer actor)||!(level.getBlockEntity(pos) instanceof NativeStorageEntity owner)||!owner.stillValid(actor))return InteractionResult.FAIL;actor.openMenu(owner);return InteractionResult.CONSUME;}
 @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(hand!=InteractionHand.MAIN_HAND)return ItemInteractionResult.FAIL;if(stack.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return openConfiguration(player,level,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
 @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(player.isCreative()&&player.isShiftKeyDown())return openConfiguration(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.FAIL;return inventory(player,level,pos);}
 @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){return NativeStorageItemSettings.pick(asItem(),level.getBlockEntity(pos) instanceof NativeStorageEntity owner?owner:null,level.registryAccess());}
 @Override protected java.util.List<ItemStack> getDrops(BlockState state,LootParams.Builder context){return java.util.List.of(NativeStorageItemSettings.drop(asItem(),context.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof NativeStorageEntity owner?owner:null,context.getLevel().registryAccess()));}
 @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeStorageEntity owner)owner.forceRecovery(owner.inert()&&(player.isCreative()||!state.canHarvestBlock(level,pos,player)));return super.playerWillDestroy(level,pos,state,player);}
 @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())&&!level.isClientSide&&!com.vandorlabs.neoforge.placement.NativeStationaryPlacement.mutating(level,pos)&&level.getBlockEntity(pos) instanceof NativeStorageEntity owner){if(owner.inert()){if(owner.takeForcedRecovery())Block.popResource(level,pos,NativeStorageItemSettings.drop(asItem(),owner,level.registryAccess()));}else{Containers.dropContents(level,pos,owner);owner.droppedContents();}NativeStorageLifecycle.comparators((ServerLevel)level,pos,this);}super.onRemove(state,level,pos,next,moving);}
 @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
 @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
