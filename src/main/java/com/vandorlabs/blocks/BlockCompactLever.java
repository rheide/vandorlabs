package com.vandorlabs.blocks;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.IBlockAccess;

/** Small, simple lever: same behavior as the industrial lever (wall mount,
 * toggle, redstone, pop-off) with tighter selection bounds for its smaller
 * 3D model. Hand-maintained custom block like its parent.
 */
public class BlockCompactLever extends BlockIndustrialLever {

    public BlockCompactLever() {
        super("compact_lever");
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        // Tighter union of both handle positions for the smaller model.
        double a = 2.75 / 16.0, b = 13.25 / 16.0, front = 9.5 / 16.0;
        EnumFacing facing = state.getValue(FACING);
        if (state.getValue(FLOOR)) {
            // Rotate the wall model's bounds onto its floor support, just
            // like the blockstate's x=270 model rotation. East/west swaps
            // the footprint axes; both handle positions remain selectable.
            double along0=3.75/16.0,along1=12.25/16.0;
            return facing.getAxis()==EnumFacing.Axis.X
                    ?new AxisAlignedBB(along0,0,a,along1,1-front,b)
                    :new AxisAlignedBB(a,0,along0,b,1-front,along1);
        }
        switch (facing) {
            case SOUTH: return new AxisAlignedBB(a, 3.75/16, 0, b, 12.25/16, 1-front);
            case EAST:  return new AxisAlignedBB(0, 3.75/16, a, 1-front, 12.25/16, b);
            case WEST:  return new AxisAlignedBB(front, 3.75/16, a, 1, 12.25/16, b);
            case NORTH:
            default:    return new AxisAlignedBB(a, 3.75/16, front, b, 12.25/16, 1);
        }
    }
}
