package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Surface coordinates shared by placement and porthole joining. */
public final class DiagonalPanelGeometry {
    private DiagonalPanelGeometry() { }

    /** Reverse the slope while retaining the shallow panel's upper/lower band. */
    public static IBlockState reverseSlope(IBlockState state, boolean shallow) {
        return shallow ? state.withProperty(BlockProgrammableWall.FACING,
                state.getValue(BlockProgrammableWall.FACING).getOpposite())
                : state.withProperty(BlockProgrammableWall.INVERTED,
                        !state.getValue(BlockProgrammableWall.INVERTED));
    }

    /** Candidate cells in the next row; never reserves or populates those cells. */
    public static java.util.List<BlockPos> rowCandidates(BlockPos pos,IBlockState state,int mode,boolean positive) {
        java.util.List<BlockPos> result=new java.util.ArrayList<>();
        EnumFacing facing=state.getValue(BlockProgrammableWall.FACING);
        BlockPos next=pos.offset(mode==2?facing.getOpposite():EnumFacing.UP,positive?1:-1);
        for(int offset=-1;offset<=1;offset++)
            result.add(next.offset(mode==2?EnumFacing.UP:facing.getOpposite(),offset));
        return result;
    }

    public static boolean coveredEnd(net.minecraft.world.World world,BlockPos pos,IBlockState state,boolean upper) {
        int mode=BlockProgrammableWall.geometry(world,pos);
        for(BlockPos next:rowCandidates(pos,state,mode,upper)) {
            if(!world.isBlockLoaded(next))continue;
            IBlockState other=world.getBlockState(next);
            if(other.getBlock()!=state.getBlock() || BlockProgrammableWall.geometry(world,next)!=mode
                    || BlockProgrammableWall.fill(world,pos)!=BlockProgrammableWall.fill(world,next))continue;
            if(endsMeet(pos,state,next,other,mode,upper))return true;
        }
        return false;
    }

    /** End cross-sections may meet at either a straight continuation or a bend. */
    private static boolean endsMeet(BlockPos a,IBlockState as,BlockPos b,IBlockState bs,int mode,boolean upper) {
        EnumFacing af=as.getValue(BlockProgrammableWall.FACING),bf=bs.getValue(BlockProgrammableWall.FACING);
        if(af.getAxis()!=bf.getAxis())return false;
        boolean ai=as.getValue(BlockProgrammableWall.INVERTED),bi=bs.getValue(BlockProgrammableWall.INVERTED);
        double t=upper?1:0,span=com.vandorlabs.render.DiagonalWallGeometry.span(mode);
        double normal=PanelPlane.axis(b.subtract(a),af.getOpposite());
        double ac=(ai?1-t:t)*span;
        double bc;
        if(mode==2) {
            ac+=ai?.5:0;
            double bt=t-normal;
            if(bf!=af)bt=1-bt;
            bc=b.getY()-a.getY()+(bi?.5:0)+(bi?1-bt:bt)*span;
        } else {
            double bt=a.getY()+t-b.getY();
            bc=(bi?1-bt:bt)*span;
            if(bf!=af)bc=1-bc;
            bc+=normal;
        }
        return Math.abs(ac-bc)<1e-8;
    }

    public static boolean samePlane(BlockPos a, IBlockState as, BlockPos b,
            IBlockState bs, int mode) {
        EnumFacing af = as.getValue(BlockProgrammableWall.FACING);
        EnumFacing bf = bs.getValue(BlockProgrammableWall.FACING);
        if (bf != af && bf != af.getOpposite()) return false;
        boolean ai = as.getValue(BlockProgrammableWall.INVERTED);
        boolean bi = bs.getValue(BlockProgrammableWall.INVERTED);
        double span = com.vandorlabs.render.DiagonalWallGeometry.span(mode);
        double slopeA = ai ? -span : span, slopeB = bi ? -span : span;
        double baseA = ai ? span : 0, baseB = bi ? span : 0;
        if (mode == 2) {
            baseA += ai ? .5 : 0;
            baseB += bi ? .5 : 0;
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
