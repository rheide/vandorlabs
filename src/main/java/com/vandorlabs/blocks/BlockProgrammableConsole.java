package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Floor-standing counterpart to the Programmable Viewscreen. Its baked model
 * supplies the one-pixel base; the tile renderer supplies the selectable
 * input deck, solid inclined housing, and live screen surface.
 */
public class BlockProgrammableConsole extends BlockAnimatedScreenSelector {

    public static final String NAME = "programmable_console";

    public BlockProgrammableConsole() {
        super(NAME);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        // Like the existing consoles, face the player and remain floor-oriented.
        return getDefaultState().withProperty(FACING,
                placementFacing(worldIn, pos, side, placer));
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
}
