package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Loaded neighbours can trim an overhang without taking ownership of their cells. */
public final class DiagonalNeighbourBounds {
    private DiagonalNeighbourBounds() { }

    public static double[] local(IBlockAccess world,BlockPos pos,IBlockState state,int mode,double scale) {
        double[] bounds={-2,-2,-2,3,3,3};
        java.util.List<double[]> obstacles=new java.util.ArrayList<>();
        EnumFacing facing=state.getValue(BlockProgrammableWall.FACING);
        boolean inverted=state.getValue(BlockProgrammableWall.INVERTED);
        // Local axes are the renderer's north-facing X/Y/Z axes.
        EnumFacing[] positive={facing.rotateY(),EnumFacing.UP,facing.getOpposite()};
        for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++) {
            EnumFacing direction=side==0?positive[axis].getOpposite():positive[axis];
            BlockPos next=pos.offset(direction);
            if(world instanceof World && !((World)world).isBlockLoaded(next))continue;
            IBlockState other=world.getBlockState(next);
            boolean solid=other.isFullCube();
            boolean opposing=false;
            if(other.getBlock() instanceof BlockProgrammableWall
                    && ((BlockProgrammableWall)other.getBlock()).isDiagonalShape()
                    && BlockProgrammableWall.geometry(world,next)==mode
                    && (mode==2?axis==1:axis==2)) {
                EnumFacing of=other.getValue(BlockProgrammableWall.FACING);
                if(of.getAxis()==facing.getAxis()) {
                    boolean oi=other.getValue(BlockProgrammableWall.INVERTED) ^ (of!=facing);
                    opposing=oi!=inverted;
                }
            }
            if(opposing)bounds[axis+side*3]=side==0?0:1;
            if(solid) {
                double[] box={0,0,0,1,1,1};
                box[axis]=side==0?-1:1-1.0/4096;
                box[axis+3]=side==0?1.0/4096:2;
                obstacles.add(box);
            }
        }
        double[] result=new double[6+6*obstacles.size()];
        System.arraycopy(bounds,0,result,0,6);
        for(int i=0;i<obstacles.size();i++)System.arraycopy(obstacles.get(i),0,result,6+6*i,6);
        for(int i=0;i<result.length;i++)result[i]*=scale;
        return result;
    }

    public static void clipCollision(java.util.List<AxisAlignedBB> boxes,int start,
            IBlockAccess world,BlockPos pos,IBlockState state,int mode) {
        double[] bounds=local(world,pos,state,mode,1);
        EnumFacing facing=state.getValue(BlockProgrammableWall.FACING);
        AxisAlignedBB clearance=box(bounds,0,facing,pos);
        java.util.List<AxisAlignedBB> pieces=new java.util.ArrayList<>();
        while(boxes.size()>start) {
            AxisAlignedBB original=boxes.remove(start);
            if(original.intersects(clearance))pieces.add(original.intersect(clearance));
        }
        for(int offset=6;offset<bounds.length;offset+=6) {
            AxisAlignedBB obstacle=box(bounds,offset,facing,pos);
            java.util.List<AxisAlignedBB> next=new java.util.ArrayList<>();
            for(AxisAlignedBB piece:pieces) {
                if(!piece.intersects(obstacle)) { next.add(piece);continue; }
                AxisAlignedBB cut=piece.intersect(obstacle);
                add(next,piece.minX,piece.minY,piece.minZ,cut.minX,piece.maxY,piece.maxZ);
                add(next,cut.maxX,piece.minY,piece.minZ,piece.maxX,piece.maxY,piece.maxZ);
                add(next,cut.minX,piece.minY,piece.minZ,cut.maxX,cut.minY,piece.maxZ);
                add(next,cut.minX,cut.maxY,piece.minZ,cut.maxX,piece.maxY,piece.maxZ);
                add(next,cut.minX,cut.minY,piece.minZ,cut.maxX,cut.maxY,cut.minZ);
                add(next,cut.minX,cut.minY,cut.maxZ,cut.maxX,cut.maxY,piece.maxZ);
            }
            pieces=next;
        }
        boxes.addAll(pieces);
    }

    private static AxisAlignedBB box(double[] b,int i,EnumFacing facing,BlockPos pos) {
        return PanelPlacement.rotateFromNorth(new AxisAlignedBB(b[i],b[i+1],b[i+2],b[i+3],b[i+4],b[i+5]),facing).offset(pos);
    }

    private static void add(java.util.List<AxisAlignedBB> boxes,double x0,double y0,double z0,double x1,double y1,double z1) {
        if(x1>x0 && y1>y0 && z1>z0)boxes.add(new AxisAlignedBB(x0,y0,z0,x1,y1,z1));
    }
}
