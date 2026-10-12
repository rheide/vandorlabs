package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Shared wall/glass compatibility and canonical four-pixel panel coordinates. */
public final class PanelJoins {
    public static boolean diagonal(IBlockState state){return state.getBlock() instanceof BlockProgrammableWall && ((BlockProgrammableWall)state.getBlock()).isDiagonalShape() && !((BlockProgrammableWall)state.getBlock()).isPortholeShape();}
    public static boolean flat(IBlockState state){return state.getBlock() instanceof BlockProgrammableGlass || state.getBlock() instanceof BlockProgrammableWall && ((BlockProgrammableWall)state.getBlock()).getShape()==BlockProgrammableWall.Shape.PLAIN;}
    public static EnumFacing facing(IBlockState state){return state.getBlock() instanceof BlockProgrammableGlass ? (state.getValue(BlockGlassWall.ROTATED)?EnumFacing.EAST:EnumFacing.NORTH):state.getValue(BlockProgrammableWall.FACING);}
    public static double near(IBlockState state){return PanelDepth.start(state.getValue(state.getBlock() instanceof BlockProgrammableGlass?BlockGlassWall.DEPTH:BlockProgrammableWall.DEPTH));}
    public static boolean enabled(IBlockAccess world,BlockPos pos){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);return !(tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass) || ((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).isJoin();}
    /** Match a flat panel's edge to an aligned flat face or a diagonal endpoint. */
    public static boolean connectsFlat(IBlockAccess world,BlockPos flatPos,IBlockState flatState,BlockPos next){
        if(!enabled(world,flatPos) || !enabled(world,next))return false;
        IBlockState other=world.getBlockState(next);
        EnumFacing facing=facing(flatState);
        if(flat(other)){
            EnumFacing of=facing(other);
            if(of.getAxis()!=facing.getAxis())return false;
            double n=near(other);if(of!=facing)n=12-n;
            return n==near(flatState);
        }
        if(!diagonal(other) || BlockProgrammableWall.halfHeight(world,next)
                || facing(other).getAxis()!=facing.getAxis() || Math.abs(flatPos.getY()-next.getY())!=1)return false;
        boolean upper=flatPos.getY()>next.getY();
        double end=BlockProgrammableWall.flatEnd(other,world,next,upper);
        if(Double.isNaN(end))return false;
        double flatNear=near(flatState)/16;
        if(facing!=facing(other))flatNear=.75-flatNear;
        int dx=flatPos.getX()-next.getX(),dz=flatPos.getZ()-next.getZ();
        EnumFacing normal=facing(other).getOpposite();
        flatNear+=dx*normal.getFrontOffsetX()+dz*normal.getFrontOffsetZ();
        return Math.abs(end-flatNear)<1e-8;
    }
    private PanelJoins() { }
}
