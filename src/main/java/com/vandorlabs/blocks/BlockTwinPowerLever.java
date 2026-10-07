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
        new AxisAlignedBB(0.2187500000,0.1243495225,0.3952199437,0.7812500000,0.7455815188,1.0000000000),
        new AxisAlignedBB(0.2187500000,0.1243495225,0.0000000000,0.7812500000,0.7455815188,0.6047800563),
        new AxisAlignedBB(0.0000000000,0.1243495225,0.2187500000,0.6047800563,0.7455815188,0.7812500000),
        new AxisAlignedBB(0.3952199437,0.1243495225,0.2187500000,1.0000000000,0.7455815188,0.7812500000),
        new AxisAlignedBB(0.2187500000,0.0000000000,0.1243495225,0.7812500000,0.6047800563,0.7455815188),
        new AxisAlignedBB(0.2544184812,0.0000000000,0.2187500000,0.8756504775,0.6047800563,0.7812500000),
        new AxisAlignedBB(0.0937500000,0.1022524356,0.4147524356,0.9062500000,0.8977475644,1.0000000000),
        new AxisAlignedBB(0.0937500000,0.1022524356,0.0000000000,0.9062500000,0.8977475644,0.5852475644),
        new AxisAlignedBB(0.0000000000,0.1022524356,0.0937500000,0.5852475644,0.8977475644,0.9062500000),
        new AxisAlignedBB(0.4147524356,0.1022524356,0.0937500000,1.0000000000,0.8977475644,0.9062500000),
        new AxisAlignedBB(0.0937500000,0.0000000000,0.1022524356,0.9062500000,0.5852475644,0.8977475644),
        new AxisAlignedBB(0.1022524356,0.0000000000,0.0937500000,0.8977475644,0.5852475644,0.9062500000),
    };
    public BlockTwinPowerLever(String name,boolean large){super(name);offset=large?6:0;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        EnumFacing face=state.getValue(FACING);
        int index=state.getValue(FLOOR)?(face.getAxis()==EnumFacing.Axis.X?5:4):face==EnumFacing.NORTH?0:face==EnumFacing.SOUTH?1:face==EnumFacing.EAST?2:3;
        return BOUNDS[offset+index];
    }
}
