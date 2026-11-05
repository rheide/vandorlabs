package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;

/** Installed mounted-control behavior needed by the shared latch, transmission and dialog host. */
public interface NativeMountedControl {
    boolean isMomentary();
    /** Negative means this mounted family has no PowerLeverSize option. */
    default int defaultPowerLeverSize(){return -1;}
    default boolean isTwinPowerLever(){return false;}
    boolean powered(BlockState state);
    BlockState withPowered(BlockState state,boolean value);
    /** Direction from this control toward its physical support. */
    Direction supportDirection(BlockState state);
    default boolean supported(BlockState state,LevelReader level,BlockPos pos){Direction toward=supportDirection(state);BlockPos support=pos.relative(toward);return level.hasChunkAt(support)&&level.getBlockState(support).isFaceSturdy(level,support,toward.getOpposite());}
    default int physicalSignal(BlockState state,BlockGetter level,BlockPos pos){if(!powered(state))return 0;return level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile?(tile.localRedstone()?tile.getOutputLevel():0):15;}
    default void dropUnsupported(BlockState state,ServerLevel level,BlockPos pos){if(!level.hasChunkAt(pos)||level.getBlockState(pos).getBlock()!=this)return;Block.dropResources(state,level,pos,level.getBlockEntity(pos));level.removeBlock(pos,false);}
}
