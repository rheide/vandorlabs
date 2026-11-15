package com.vandorlabs.neoforge.copier;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

/** Source horizontal controller: paired pads sit on its left/right and the material chest behind. */
public final class NativeCopyControllerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE=BooleanProperty.create("active");
    public NativeCopyControllerBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ACTIVE,false));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,ACTIVE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeCopyControllerEntity(NativeCopyBindings.ENTITY,pos,state);}
    @Override public void setPlacedBy(Level world,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity actor,ItemStack stack){super.setPlacedBy(world,pos,state,actor,stack);if(actor instanceof net.minecraft.server.level.ServerPlayer player&&world.getBlockEntity(pos) instanceof NativeCopyControllerEntity tile)tile.authorize(player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,Level world,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(hand==InteractionHand.MAIN_HAND&&held.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return NativeCopyBindings.open(player,world,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;return held.isEmpty()?ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION:ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player player,BlockHitResult hit){return NativeCopyBindings.open(player,world,pos)?InteractionResult.sidedSuccess(world.isClientSide):InteractionResult.FAIL;}
    @Override protected void neighborChanged(BlockState state,Level world,BlockPos pos,Block changed,BlockPos from,boolean moving){if(!world.isClientSide&&world.getBlockEntity(pos) instanceof NativeCopyControllerEntity tile){tile.surfaceChanged();}}
    @Override protected void tick(BlockState state,ServerLevel world,BlockPos pos,RandomSource random){if(world.getBlockEntity(pos) instanceof NativeCopyControllerEntity tile)tile.scheduled();}
    @Override protected void onRemove(BlockState state,Level world,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())&&!world.isClientSide&&world.getBlockEntity(pos) instanceof NativeCopyControllerEntity tile)tile.release();super.onRemove(state,world,pos,next,moving);}
    @Override protected java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder context){var stack=new ItemStack(asItem());if(context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof NativeCopyControllerEntity tile&&tile.inert())stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(tile.saveWithId(tile.getLevel().registryAccess())));return java.util.List.of(stack);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
