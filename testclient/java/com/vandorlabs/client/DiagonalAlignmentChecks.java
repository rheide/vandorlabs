package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.render.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Owner-approved repeated slopes and neighbour trimming regressions. */
final class DiagonalAlignmentChecks {
    static void run() {
        BlockProgrammableWall wall=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
        BlockPos root=new BlockPos(8,100,8);
        for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true}) {
            IBlockState first=wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing)
                    .withProperty(BlockProgrammableWall.INVERTED,inverted);
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            for(int row=0;row<6;row++) {
                boolean reverse=mode!=1 && row%2==1;
                int advance=mode==2?row:mode==1?(inverted?-row:row):inverted?-(row+1)/2:row/2;
                int rise=mode==2?(inverted?-row/2:row/2):row;
                BlockPos pos=root.offset(facing.getOpposite(),advance).up(rise);
                IBlockState state=first.withProperty(BlockProgrammableWall.FACING,reverse?facing.getOpposite():facing)
                        .withProperty(BlockProgrammableWall.INVERTED,inverted^reverse);
                world.setBlockState(pos,state,2);
                ((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).setDiagonalGeometry(mode,0);
                require(DiagonalPanelGeometry.samePlane(root,first,pos,state,mode),"six-piece slope drifts, mode "+mode);
                double[][] leaf=DiagonalTrapdoorGeometry.corners(mode,inverted^reverse,0,false,false,0);
                for(int i=0;i<8;i++) {
                    double along=mode==2?leaf[i][2]:leaf[i][1];
                    double near=DiagonalWallGeometry.near(mode,inverted^reverse,along)+DiagonalWallGeometry.band(mode,inverted^reverse);
                    double depth=mode==2?leaf[i][1]:leaf[i][2];
                    int bit=mode==2?2:4;
                    require(Math.abs(depth-near-((i&bit)==0?1/16D:3/16D))<1e-9,"trapdoor leaves wall plane");
                }
            }
        }
        // A neighbouring cube trims only the overlapping part of the exposed face.
        double[] clip={-2,-2,-2,3,3,3, 1-1.0/4096,0,0,2,1,1};
        List<double[]> visible=DiagonalMeshClip.quads(clip,new double[]{1,0,0,0,0},new double[]{1,0,2,1,0},
                new double[]{1,1,2,1,1},new double[]{1,1,0,0,1});
        require(!visible.isEmpty(),"neighbour removed the exposed part of the face");
        for(double[] v:visible)require(v[2]>=1,"face remains inside neighbouring cube");
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        IBlockState state=wall.getDefaultState();world.setBlockState(root,state,2);
        double[] empty=DiagonalNeighbourBounds.local(world,root,state,0,1);
        world.setBlockState(root.east(),Blocks.STONE.getDefaultState(),2);
        require(DiagonalNeighbourBounds.local(world,root,state,0,1).length>empty.length,"neighbour not detected");
        world.setBlockToAir(root.east());
        require(Arrays.equals(empty,DiagonalNeighbourBounds.local(world,root,state,0,1)),"removed neighbour still clips wall");
        System.out.println("PASS: six-piece diagonal planes, all three shapes and directions, leaf insets and neighbour clipping");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
