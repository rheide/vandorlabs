package com.vandorlabs.neoforge.screen;

import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One-cell stationary cockpit console sharing the two-slot Screen controller. */
public final class NativeYokeScreenBlock extends NativeScreenBlock {
    public NativeYokeScreenBlock(Properties properties) {
        super(ScreenFamily.CONSOLE, properties, VandorLabsBootstrap.PROGRAMMABLE_ENTITY,
            NativeScreenRegistrations.MENU);
    }

    @Override protected DirectionProperty facingProperty() { return HORIZONTAL_FACING; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> states) {
        states.add(HORIZONTAL_FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return NativeYokeGeometry.of(state).outline();
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return NativeYokeGeometry.of(state).collision();
    }
}
