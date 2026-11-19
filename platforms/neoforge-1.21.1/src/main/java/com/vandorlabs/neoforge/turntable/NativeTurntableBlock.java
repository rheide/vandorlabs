package com.vandorlabs.neoforge.turntable;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.surface.NativeSurfaceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

final class NativeTurntableBlock extends BaseEntityBlock {
    static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    NativeTurntableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> states) {
        states.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var towardSupport = context.getClickedFace().getOpposite();
        var world = context.getLevel();
        var support = context.getClickedPos().relative(towardSupport);
        var facing = towardSupport.getAxis().isHorizontal() && world.hasChunkAt(support)
                && world.getBlockState(support).getBlock() instanceof NativeSurfaceBlock
                ? towardSupport : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NativeTurntableEntity(pos, state); }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
}
