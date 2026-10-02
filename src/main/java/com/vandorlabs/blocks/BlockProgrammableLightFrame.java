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
        return ProgrammableLightShape.world(state,world,pos);
    }
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
}
