package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Surface coordinates shared by placement and porthole joining. */
public final class DiagonalPanelGeometry {
    private DiagonalPanelGeometry() { }

    public static boolean samePlane(BlockPos a, IBlockState as, BlockPos b,
            IBlockState bs, int mode) {
        EnumFacing af = as.getValue(BlockProgrammableWall.FACING);
        EnumFacing bf = bs.getValue(BlockProgrammableWall.FACING);
        if (bf != af && bf != af.getOpposite()) return false;
        boolean ai = as.getValue(BlockProgrammableWall.INVERTED);
        boolean bi = bs.getValue(BlockProgrammableWall.INVERTED);
        double span = mode == 1 ? .75 : .375;
        double slopeA = ai ? -span : span, slopeB = bi ? -span : span;
        double baseA = .125 + (ai ? span : 0), baseB = .125 + (bi ? span : 0);
        if (mode == 2) {
            baseA += ai ? .375 : 0;
            baseB += bi ? .375 : 0;
            if (bf != af) { baseB += slopeB; slopeB = -slopeB; }
            baseB += b.getY() - a.getY()
                    - slopeB * PanelPlane.axis(b.subtract(a), af.getOpposite());
        } else {
            if (bf != af) { baseB = 1 - baseB; slopeB = -slopeB; }
            baseB += PanelPlane.axis(b.subtract(a), af.getOpposite())
                    - slopeB * (b.getY() - a.getY());
        }
        return Math.abs(slopeA - slopeB) < 1e-8 && Math.abs(baseA - baseB) < 1e-8;
    }
}
