package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** A one-pixel-deep light mounted flush against its supporting face. */
public final class BlockProgrammableLightFrame extends BlockProgrammableLight {
    public BlockProgrammableLightFrame() { super("programmable_light_frame"); }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos) {
        EnumFacing facing=state.getValue(FACING);
        if(facing==EnumFacing.UP)return new AxisAlignedBB(0,15/16D,0,1,1,1);
        if(facing==EnumFacing.DOWN)return new AxisAlignedBB(0,0,0,1,1/16D,1);
        return PanelPlacement.rotateFromNorth(new AxisAlignedBB(0,0,0,1,1,1/16D),facing);
    }
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
}
