package com.vandorlabs.items;

import com.vandorlabs.blocks.BlockVandorDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Both handheld configuration tools address a door through its lower tile. */
public final class ProgrammableTarget {
    private ProgrammableTarget() { }

    public static BlockPos settingsPos(World world, BlockPos clicked) {
        IBlockState state = world.getBlockState(clicked);
        if (state.getBlock() instanceof BlockVandorDoor
                && state.getValue(BlockVandorDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER)
            return clicked.down();
        if (state.getBlock() instanceof BlockDoor
                && state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER)
            return clicked.down();
        return clicked;
    }
}
