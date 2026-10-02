package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** One local box for the renderer, selection and collision of every light shape. */
public final class ProgrammableLightShape {
    private ProgrammableLightShape() { }
    public static AxisAlignedBB local(IBlockState state,boolean small) {
        double x0=0,x1=1,y0=0,y1=1,z0=0,z1=1;
        EnumFacing facing=state.getValue(BlockAnimatedScreenSelector.FACING);
        boolean frame=state.getBlock() instanceof BlockProgrammableLightFrame;
        boolean slab=state.getBlock() instanceof BlockProgrammableLightSlab;
        if(frame)z0=15/16D;
        if(slab) {
            boolean upper=state.getValue(BlockProgrammableLightSlab.HALF)==BlockSlab.EnumBlockHalf.TOP;
            if(facing==EnumFacing.UP){z0=upper?0:.5;z1=upper?.5:1;}
            else if(facing==EnumFacing.DOWN){z0=upper?.5:0;z1=upper?1:.5;}
            else {y0=upper?.5:0;y1=upper?1:.5;}
        }
        if(small) {
            x0=.25;x1=.75;
            if(!slab || !facing.getAxis().isHorizontal()){y0=.25;y1=.75;}
            if(!frame && (!slab || facing.getAxis().isHorizontal())){z0=.25;z1=.75;}
        }
        return new AxisAlignedBB(x0,y0,z0,x1,y1,z1);
    }
    public static AxisAlignedBB world(IBlockState state,IBlockAccess world,BlockPos pos) {
        TileEntity tile=world.getTileEntity(pos);
        AxisAlignedBB b=local(state,tile instanceof TileEntityProgrammableLight && ((TileEntityProgrammableLight)tile).isSmallInput());
        EnumFacing facing=state.getValue(BlockAnimatedScreenSelector.FACING);
        if(facing==EnumFacing.UP)return new AxisAlignedBB(b.minX,1-b.maxZ,b.minY,b.maxX,1-b.minZ,b.maxY);
        if(facing==EnumFacing.DOWN)return new AxisAlignedBB(b.minX,b.minZ,1-b.maxY,b.maxX,b.maxZ,1-b.minY);
        return PanelPlacement.rotateFromNorth(b,facing);
    }
}
