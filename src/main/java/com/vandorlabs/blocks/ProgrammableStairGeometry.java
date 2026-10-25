package com.vandorlabs.blocks;

import java.util.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.render.StairGeometry;
import com.vandorlabs.render.ControlMount;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.IBlockAccess;

/** Native vectors and bounds over the shared authored stair height field. */
public final class ProgrammableStairGeometry {
    private ProgrammableStairGeometry(){}
    public static int settings(IBlockAccess world,BlockPos pos){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);return tile instanceof TileEntityAnimatedScreenSelector?((TileEntityAnimatedScreenSelector)tile).stairGeometry():8;}
    private static StairGeometry.Shape shape(BlockStairs.EnumShape value){return StairGeometry.Shape.valueOf(value.name());}
    private static boolean upper(IBlockState state){return state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP;}
    public static Vec3d transform(Vec3d p,IBlockState state,int roll){ControlMount.Point q=StairGeometry.transform(new ControlMount.Point(p.x,p.y,p.z),state.getValue(BlockStairs.FACING).getIndex(),upper(state),roll);return new Vec3d(q.x,q.y,q.z);}
    public static EnumFacing unrolledFace(IBlockState state,int roll,EnumFacing face){return EnumFacing.getFront(StairGeometry.unrolledFace(state.getValue(BlockStairs.FACING).getIndex(),upper(state),roll,face.getIndex()));}
    public static double height(int x,int z,int count,BlockStairs.EnumShape shape){return StairGeometry.height(x,z,count,shape(shape));}
    public static List<AxisAlignedBB> boxes(IBlockState state,int settings){List<AxisAlignedBB> out=new ArrayList<>();for(ControlMount.Box b:StairGeometry.boxes(state.getValue(BlockStairs.FACING).getIndex(),upper(state),shape(state.getValue(BlockStairs.SHAPE)),settings))out.add(new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));return out;}
    public static boolean solidFace(IBlockState state,int settings,EnumFacing side){return StairGeometry.solidFace(state.getValue(BlockStairs.FACING).getIndex(),upper(state),shape(state.getValue(BlockStairs.SHAPE)),settings,side.getIndex());}
}
