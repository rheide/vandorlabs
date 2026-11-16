package com.vandorlabs.neoforge.infuser;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
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
import net.minecraft.world.phys.BlockHitResult;
/** Solar furnace with the Source horizontal face convention and comparator inventory signal. */
public final class NativeInfuserBlock extends BaseEntityBlock{
 public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
 public NativeInfuserBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
 @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeInfuserEntity(pos,state);}
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,NativeInfuserBindings.ENTITY,NativeInfuserEntity::tick);}
 @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(stack.getItem() instanceof com.vandorlabs.neoforge.NativeOwnsBlockInteraction)return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;return NativeInfuserBindings.open(player,level,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
 @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return NativeInfuserBindings.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;}
 @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
 @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){return level.getBlockEntity(pos) instanceof NativeInfuserEntity tile&&!tile.inert()?net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromContainer(tile):0;}
 private ItemStack shell(NativeInfuserEntity tile,HolderLookup.Provider registries){var item=new ItemStack(this);if(tile!=null&&tile.inert())tile.saveToItem(item,registries);return item;}
 @Override protected java.util.List<ItemStack> getDrops(BlockState state,LootParams.Builder context){var raw=context.getOptionalParameter(LootContextParams.BLOCK_ENTITY);return java.util.List.of(shell(raw instanceof NativeInfuserEntity tile?tile:null,context.getLevel().registryAccess()));}
 @Override public ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult hit,net.minecraft.world.level.LevelReader level,BlockPos pos,Player player){return shell(level.getBlockEntity(pos) instanceof NativeInfuserEntity tile?tile:null,level.registryAccess());}
 @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeInfuserEntity tile){for(var item:tile.drain())Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,item);level.updateNeighbourForOutputSignal(pos,this);}super.onRemove(state,level,pos,next,moving);}
}
