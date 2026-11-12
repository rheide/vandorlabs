package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
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

    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world,
            IBlockState state, BlockPos pos, EnumFacing side) {
        // Only the backing face fills a cell boundary. The front is recessed,
        // and the narrow edges must not draw connecting wall/fence arms.
        if (side != state.getValue(FACING).getOpposite()) return BlockFaceShape.UNDEFINED;
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileEntityProgrammableLight
                && ((TileEntityProgrammableLight) tile).isSmallInput()
                ? BlockFaceShape.UNDEFINED : BlockFaceShape.SOLID;
    }
}
