package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableTrapdoor;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;

/** Released group-relative artwork coordinate mapping. */
final class ReferenceTrapdoorCoordinates {
    static double[][] coordinates(TileEntityProgrammableTrapdoor tile,IBlockState state,java.util.List<TileEntityProgrammableTrapdoor> group) {
        boolean diagonal=tile instanceof TileEntityProgrammableDiagonalTrapdoor;
        boolean tall=diagonal && tile.getPosition()!=2;
        net.minecraft.util.EnumFacing facing=state.getValue(BlockProgrammableTrapdoor.FACING);
        boolean widthX=facing.getAxis()==(diagonal?net.minecraft.util.EnumFacing.Axis.Z:net.minecraft.util.EnumFacing.Axis.X);
        boolean door=ScreenHousingTextures.isDoor(tile.getHousingTexture());
        double minU=Double.POSITIVE_INFINITY,minV=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,maxV=Double.NEGATIVE_INFINITY;
        for(TileEntityProgrammableTrapdoor leaf:group) {
            double u=widthX?leaf.getPos().getX():leaf.getPos().getZ();
            double v=tall?leaf.getPos().getY():widthX?leaf.getPos().getZ():leaf.getPos().getX();
            minU=Math.min(minU,u);maxU=Math.max(maxU,u+1);minV=Math.min(minV,v);maxV=Math.max(maxV,v+1);
        }
        double[][] closed=diagonal?com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor.corners(state,(TileEntityProgrammableDiagonalTrapdoor)tile,0)
                :tile.corners(state,0);
        // Opposing-cover lookup and bounds are invariant across the eight corners.
        if(tile.isCover()) {
            net.minecraft.util.math.BlockPos target=tile.getPos().offset(facing);double offset=tile.coverOffset();
            minU=(widthX?target.getX():target.getZ())+offset*(widthX?facing.getFrontOffsetX():facing.getFrontOffsetZ());maxU=minU+1;
            minV=(widthX?target.getZ():target.getX())+offset*(widthX?facing.getFrontOffsetZ():facing.getFrontOffsetX());maxV=minV+1;
        }
        double[][] uv=new double[8][];
        for(int i=0;i<8;i++) {
            double u=(widthX?tile.getPos().getX()+closed[i][0]:tile.getPos().getZ()+closed[i][2]);
            double v=tall?tile.getPos().getY()+closed[i][1]:widthX?tile.getPos().getZ()+closed[i][2]:tile.getPos().getX()+closed[i][0];
            double mappedU=u-minU,mappedV=tall?maxV-v:v-minV;
            if(tile.isTileTexture()){if(door){mappedV/=2;if(maxV-minV<1.5)mappedV+=.5;}}
            else{mappedU/=maxU-minU;mappedV/=maxV-minV;if(door && maxV-minV<1.5)mappedV=.5+.5*mappedV;}
            uv[i]=new double[]{mappedU,tall?mappedV:(i&2)==0?0:1,tall?(i&4)==0?0:1:mappedV};
        }
        return uv;
    }
}
