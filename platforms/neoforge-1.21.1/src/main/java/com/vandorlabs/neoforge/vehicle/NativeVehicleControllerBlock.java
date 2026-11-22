package com.vandorlabs.neoforge.vehicle;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public final class NativeVehicleControllerBlock extends BaseEntityBlock {
    NativeVehicleControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,context.getHorizontalDirection().getOpposite());
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new NativeVehicleControllerEntity(pos,state); }

    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity actor,ItemStack stack) {
        super.setPlacedBy(level,pos,state,actor,stack);
        if (!level.isClientSide && actor instanceof Player && level.getBlockEntity(pos) instanceof NativeVehicleControllerEntity tile)
            tile.placedBy(actor.getUUID());
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,Level level,BlockPos pos,
            Player player,InteractionHand hand,BlockHitResult hit) {
        if (hand==InteractionHand.MAIN_HAND && held.is(VandorLabsBootstrap.CONFIGURIZER.get()))
            return NativeVehicleBindings.open(player,level,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;
        return super.useItemOn(held,state,level,pos,player,hand,hit);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        return NativeVehicleBindings.open(player,level,pos)?InteractionResult.SUCCESS:InteractionResult.PASS;
    }
}
