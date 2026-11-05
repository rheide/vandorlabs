package com.vandorlabs.blocks;

import java.util.List;
import com.vandorlabs.render.StairGeometry;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.IBlockAccess;

/** Native point/box adapter for the shared current stair height field. */
public final class ProgrammableStairGeometry {
    private ProgrammableStairGeometry(){}
    public static final StairGeometry.Points<Vec3d> POINTS=new StairGeometry.Points<Vec3d>(){
        public Vec3d create(double x,double y,double z){return new Vec3d(x,y,z);}public double x(Vec3d p){return p.x;}public double y(Vec3d p){return p.y;}public double z(Vec3d p){return p.z;}
    };
    private static final StairGeometry.Boxes<AxisAlignedBB> BOXES=new StairGeometry.Boxes<AxisAlignedBB>(){
        public AxisAlignedBB create(double x0,double y0,double z0,double x1,double y1,double z1){return new AxisAlignedBB(x0,y0,z0,x1,y1,z1);}public double minX(AxisAlignedBB b){return b.minX;}public double minY(AxisAlignedBB b){return b.minY;}public double minZ(AxisAlignedBB b){return b.minZ;}public double maxX(AxisAlignedBB b){return b.maxX;}public double maxY(AxisAlignedBB b){return b.maxY;}public double maxZ(AxisAlignedBB b){return b.maxZ;}
    };
    private static final StairGeometry.Shape[] SHAPES=StairGeometry.Shape.values();
    public static StairGeometry.Shape shape(BlockStairs.EnumShape shape){return SHAPES[shape.ordinal()];}
    public static int settings(IBlockAccess world,BlockPos pos){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);return tile instanceof TileEntityAnimatedScreenSelector?((TileEntityAnimatedScreenSelector)tile).stairGeometry():8;}
    public static Vec3d transform(Vec3d p,IBlockState state,int roll){return StairGeometry.transform(p.x,p.y,p.z,state.getValue(BlockStairs.FACING).getIndex(),state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP,roll,POINTS);}
    public static EnumFacing unrolledFace(IBlockState state,int roll,EnumFacing face){return EnumFacing.getFront(StairGeometry.unrolledFace(state.getValue(BlockStairs.FACING).getIndex(),state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP,roll,face.getIndex(),POINTS));}
    public static double height(int x,int z,int count,BlockStairs.EnumShape shape){return StairGeometry.height(x,z,count,shape(shape));}
    public static List<AxisAlignedBB> boxes(IBlockState state,int settings){return StairGeometry.boxes(state.getValue(BlockStairs.FACING).getIndex(),state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP,shape(state.getValue(BlockStairs.SHAPE)),settings,POINTS,BOXES);}
    public static boolean solidFace(IBlockState state,int settings,EnumFacing side){return StairGeometry.solidFace(state.getValue(BlockStairs.FACING).getIndex(),state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP,shape(state.getValue(BlockStairs.SHAPE)),settings,side.getIndex(),POINTS,BOXES);}
}
