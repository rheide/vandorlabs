package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Twin-arm binary controls sharing Industrial Power Lever interaction and channels. */
public final class BlockTwinPowerLever extends BlockIndustrialLever {
    private final int offset;
    private static final AxisAlignedBB[] BOUNDS={
        new AxisAlignedBB(0.21875,0.2544184812321244,0.39521994367730057,0.78125,0.7455815187678756,1.0),
        new AxisAlignedBB(0.21875,0.2544184812321244,0.0,0.78125,0.7455815187678756,0.6047800563226995),
        new AxisAlignedBB(0.0,0.2544184812321244,0.21875,0.6047800563226995,0.7455815187678756,0.78125),
        new AxisAlignedBB(0.39521994367730057,0.2544184812321244,0.21875,1.0,0.7455815187678756,0.78125),
        new AxisAlignedBB(0.21875,0.0,0.2544184812321244,0.78125,0.6047800563226995,0.7455815187678756),
        new AxisAlignedBB(0.2544184812321244,0.0,0.21875,0.7455815187678756,0.6047800563226995,0.78125),
        new AxisAlignedBB(0.09375,0.013864087934248592,0.32636408793424854,0.90625,0.9861359120657514,1.0),
        new AxisAlignedBB(0.09375,0.013864087934248592,0.0,0.90625,0.9861359120657514,0.6736359120657515),
        new AxisAlignedBB(0.0,0.013864087934248592,0.09375,0.6736359120657515,0.9861359120657514,0.90625),
        new AxisAlignedBB(0.32636408793424854,0.013864087934248592,0.09375,1.0,0.9861359120657514,0.90625),
        new AxisAlignedBB(0.09375,0.0,0.013864087934248592,0.90625,0.6736359120657515,0.9861359120657514),
        new AxisAlignedBB(0.013864087934248592,0.0,0.09375,0.9861359120657514,0.6736359120657515,0.90625),
    };
    public BlockTwinPowerLever(String name,boolean large){super(name);offset=large?6:0;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        EnumFacing face=state.getValue(FACING);
        int index=state.getValue(FLOOR)?(face.getAxis()==EnumFacing.Axis.X?5:4):face==EnumFacing.NORTH?0:face==EnumFacing.SOUTH?1:face==EnumFacing.EAST?2:3;
        return BOUNDS[offset+index];
    }
}
