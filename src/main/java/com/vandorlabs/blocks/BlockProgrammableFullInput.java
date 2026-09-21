package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Full-square counterpart to the half-height programmable input surface. */
public class BlockProgrammableFullInput extends BlockProgrammableInput {

    public static final String NAME = "programmable_full_input";
    private static final AxisAlignedBB WALL =
            new AxisAlignedBB(0, 0, 15.0 / 16.0, 1, 1, 1);
    private static final AxisAlignedBB FLOOR =
            new AxisAlignedBB(0, 7.0 / 16.0, 0, 1, 0.5, 1);
    private static final AxisAlignedBB TOP =
            new AxisAlignedBB(0, 15.0 / 16.0, 0, 1, 1, 1);

    public BlockProgrammableFullInput() {
        super(NAME);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        AxisAlignedBB local = state.getValue(KEYBOARD)
                ? (state.getValue(UPPER) ? TOP : FLOOR) : WALL;
        return rotateFromNorth(local, state.getValue(FACING));
    }
}
