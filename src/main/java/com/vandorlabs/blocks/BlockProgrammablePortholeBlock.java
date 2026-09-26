package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Full-depth version of the configurable porthole wall. */
public class BlockProgrammablePortholeBlock extends BlockProgrammableWall {
    public BlockProgrammablePortholeBlock() {
        super("programmable_porthole_block", Shape.PORTHOLE);
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING,
                placementFacing(world, pos, side, placer));
    }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state,
            IBlockAccess world, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }
}
