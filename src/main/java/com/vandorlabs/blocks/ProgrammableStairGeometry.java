package com.vandorlabs.blocks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
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
    // All native stairs have 4 facings, 2 halves, 5 corners, 5 treads and 3 rolls.
    // No world, tile, texture or blockstate is retained. Even if every pose is used,
    // this retains only 40,920 immutable AABBs (about 3 MiB including the lists;
    // the exact byte size depends on the JVM object layout).
    private static final AtomicReferenceArray<Geometry> GEOMETRY=new AtomicReferenceArray<>(4*2*5*5*3);
    private static final EnumFacing[][] UNROLLED=unrolledFaces();

    private static final class Geometry {
        final List<AxisAlignedBB> boxes;
        // Low six bits are the answers, high six bits mark sampled faces. Each
        // answer is published with its presence bit in the same volatile write.
        private volatile int solid;
        Geometry(List<AxisAlignedBB> boxes){this.boxes=Collections.unmodifiableList(Arrays.asList(boxes.toArray(new AxisAlignedBB[0])));}
        boolean solidFace(EnumFacing side){
            int bit=1<<side.getIndex(),sampled=solid;
            if((sampled&(bit<<6))!=0)return (sampled&bit)!=0;
            boolean value=StairGeometry.solidFace(boxes,side.getIndex(),BOXES);
            synchronized(this){solid|=(bit<<6)|(value?bit:0);}
            return value;
        }
    }
    private static Geometry geometry(IBlockState state,int settings){
        int tread=settings&31,roll=settings>>5;
        // Preserve the adapter's old behavior for external, noncanonical callers;
        // settings loaded by the tile always take the finite cache path.
        if(!com.vandorlabs.persistence.StairState.valid(tread,roll))return null;
        int key=(((pose(state,roll)*5)+state.getValue(BlockStairs.SHAPE).ordinal())*5)+Integer.numberOfTrailingZeros(tread);
        Geometry cached=GEOMETRY.get(key);
        if(cached!=null)return cached;
        Geometry built=new Geometry(buildBoxes(state,settings));
        return GEOMETRY.compareAndSet(key,null,built)?built:GEOMETRY.get(key);
    }
    private static int pose(IBlockState state,int roll){
        return (state.getValue(BlockStairs.FACING).getHorizontalIndex()*2
                +(state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP?1:0))*3+roll;
    }
    private static EnumFacing[][] unrolledFaces(){
        EnumFacing[][] faces=new EnumFacing[4*2*3][6];
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int half=0;half<2;half++)for(int roll=0;roll<3;roll++){
            int pose=(facing.getHorizontalIndex()*2+half)*3+roll;
            for(EnumFacing side:EnumFacing.values())faces[pose][side.getIndex()]=EnumFacing.getFront(
                    StairGeometry.unrolledFace(facing.getIndex(),half!=0,roll,side.getIndex(),POINTS));
        }
        return faces;
    }
    public static StairGeometry.Shape shape(BlockStairs.EnumShape shape){return SHAPES[shape.ordinal()];}
    public static int settings(IBlockAccess world,BlockPos pos){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);return tile instanceof TileEntityAnimatedScreenSelector?((TileEntityAnimatedScreenSelector)tile).stairGeometry():8;}
    public static Vec3d transform(Vec3d p,IBlockState state,int roll){return StairGeometry.transform(p.x,p.y,p.z,state.getValue(BlockStairs.FACING).getIndex(),state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP,roll,POINTS);}
    public static EnumFacing unrolledFace(IBlockState state,int roll,EnumFacing face){return roll==0?face:roll>0&&roll<=2?UNROLLED[pose(state,roll)][face.getIndex()]:EnumFacing.getFront(StairGeometry.unrolledFace(state.getValue(BlockStairs.FACING).getIndex(),state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP,roll,face.getIndex(),POINTS));}
    public static double height(int x,int z,int count,BlockStairs.EnumShape shape){return StairGeometry.height(x,z,count,shape(shape));}
    private static List<AxisAlignedBB> buildBoxes(IBlockState state,int settings){return StairGeometry.boxes(state.getValue(BlockStairs.FACING).getIndex(),state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP,shape(state.getValue(BlockStairs.SHAPE)),settings,POINTS,BOXES);}
    /** Canonical settings return shared immutable boxes; copy the list before modifying it. */
    public static List<AxisAlignedBB> boxes(IBlockState state,int settings){Geometry cached=geometry(state,settings);return cached==null?buildBoxes(state,settings):cached.boxes;}
    public static boolean solidFace(IBlockState state,int settings,EnumFacing side){Geometry cached=geometry(state,settings);return cached==null?StairGeometry.solidFace(buildBoxes(state,settings),side.getIndex(),BOXES):cached.solidFace(side);}
}
