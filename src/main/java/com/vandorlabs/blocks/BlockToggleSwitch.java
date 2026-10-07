package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Compact two-position slider using the Rocker Switch's latch and channel behavior. */
public final class BlockToggleSwitch extends BlockVandorSwitch {
    public BlockToggleSwitch(String name){super(name,false);}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        EnumFacing face=state.getValue(FACING);
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        if(face.getAxis()==EnumFacing.Axis.Y && tile instanceof com.vandorlabs.tiles.TileEntityRedstoneChannel)state=state.withProperty(ROTATION,((com.vandorlabs.tiles.TileEntityRedstoneChannel)tile).getMountRotation());
        double width=5/16D,length=4/16D,depth=2.375/16D;
        if(face.getAxis()==EnumFacing.Axis.Y && state.getValue(ROTATION)%2==1){double swap=width;width=length;length=swap;}
        switch(face){
            case UP:return new AxisAlignedBB(width,0,length,1-width,depth,1-length);
            case DOWN:return new AxisAlignedBB(width,1-depth,length,1-width,1,1-length);
            case SOUTH:return new AxisAlignedBB(width,length,0,1-width,1-length,depth);
            case EAST:return new AxisAlignedBB(0,length,width,depth,1-length,1-width);
            case WEST:return new AxisAlignedBB(1-depth,length,width,1,1-length,1-width);
            default:return new AxisAlignedBB(width,length,1-depth,1-width,1-length,1);
        }
    }
}
