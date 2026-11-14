package com.vandorlabs.neoforge.ports;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;

/** Six-sided ports use ordinary chunk models; ticking only moves resources on the server. */
public final class NativePortBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public final NativePortKind kind;
    public NativePortBlock(NativePortKind kind,Properties properties){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getNearestLookingDirection().getOpposite());}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return switch(kind){case POWER->new NativePowerPortEntity(pos,state);case FLUID->new NativeFluidPortEntity(pos,state);case MATTER->new NativeMatterPortEntity(pos,state);};}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,NativePortBindings.ENTITIES.get(kind),NativeResourcePortEntity::tick);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(level.getBlockEntity(pos) instanceof NativeResourcePortEntity owner)owner.neighborChanged();}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return NativePortBindings.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.FAIL;}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(hand!=InteractionHand.MAIN_HAND||stack.getItem() instanceof BlockItem)return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;return NativePortBindings.open(player,level,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;}
    private ItemStack pick(BlockEntity tile){var stack=new ItemStack(asItem());if(tile instanceof NativeResourcePortEntity owner){var tag=owner.saveWithId(owner.getLevel().registryAccess());stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(tag));}return stack;}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){return pick(level.getBlockEntity(pos));}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder context){return List.of(pick(context.getOptionalParameter(LootContextParams.BLOCK_ENTITY)));}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
