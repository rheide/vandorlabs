package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Half-height console with independently selectable deck and incline art. */
public class BlockProgrammableHalfConsole extends BlockAnimatedScreenSelector {

    public static final String NAME = "programmable_half_console";

    public BlockProgrammableHalfConsole() {
        super(NAME);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING,
                placementFacing(world, pos, side, placer));
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        net.minecraft.tileentity.TileEntity raw=source.getTileEntity(pos);
        double offset=raw instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector
                && ((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)raw).isCeilingMounted()
                ?com.vandorlabs.render.InputSurfaceLayout.ceilingStart(16,
                        ((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)raw).getCeilingPosition(1))/16:0;
        return PanelPlacement.rotateFromNorth(new AxisAlignedBB(0,0,offset,1,.5,1+offset),
                state.getValue(FACING));
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }
}
