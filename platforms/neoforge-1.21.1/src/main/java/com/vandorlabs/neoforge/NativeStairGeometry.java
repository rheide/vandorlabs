package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.StairState;
import com.vandorlabs.render.StairGeometry;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Native projection of the current shared height field; no registry, lifecycle or world access. */
public final class NativeStairGeometry {
    public static final StairGeometry.Points<Vec3> POINTS=new StairGeometry.Points<>(){
        public Vec3 create(double x,double y,double z){return point(x,y,z);}
        public double x(Vec3 p){return p.x;}public double y(Vec3 p){return p.y;}public double z(Vec3 p){return p.z;}
    };
    private static final StairGeometry.Boxes<AABB> BOXES=new StairGeometry.Boxes<>(){
        public AABB create(double x0,double y0,double z0,double x1,double y1,double z1){return new AABB(x0,y0,z0,x1,y1,z1);}
        public double minX(AABB b){return b.minX;}public double minY(AABB b){return b.minY;}public double minZ(AABB b){return b.minZ;}
        public double maxX(AABB b){return b.maxX;}public double maxY(AABB b){return b.maxY;}public double maxZ(AABB b){return b.maxZ;}
    };
    static final int CACHE_BUDGET=8*1024*1024;
    // Fixed-domain atomic publication avoids access-queue node allocation on collision reads.
    static final int CACHE_OVERHEAD=128+600*8;
    private static final java.util.concurrent.atomic.AtomicReferenceArray<Geometry> CACHE=new java.util.concurrent.atomic.AtomicReferenceArray<>(600);
    private static final Object CACHE_LOCK=new Object();
    private static int cacheBytes=CACHE_OVERHEAD,cacheSize,nextVictim;
    private record Geometry(List<AABB> boxes,AABB bounds,VoxelShape shape,int solidFaces,int retainedCost){}
    // Forge Vec3d normalizes -0 on construction, both before and after the shared transform.
    private static Vec3 point(double x,double y,double z){return new Vec3(x==0?0:x,y==0?0:y,z==0?0:z);}
    public static Vec3 transform(Vec3 p,Direction facing,boolean upper,int roll){return StairGeometry.transform(p.x==0?0:p.x,p.y==0?0:p.y,p.z==0?0:p.z,facing.get3DDataValue(),upper,roll,POINTS);}
    public static Direction unrolledFace(Direction facing,boolean upper,int roll,Direction face){return Direction.from3DDataValue(StairGeometry.unrolledFace(facing.get3DDataValue(),upper,roll,face.get3DDataValue(),POINTS));}
    public static double height(int x,int z,int count,StairGeometry.Shape shape){return StairGeometry.height(x,z,count,shape);}
    public static List<AABB> boxes(Direction facing,boolean upper,StairGeometry.Shape shape,int settings){return geometry(facing,upper,shape,settings).boxes;}
    public static AABB bounds(Direction facing,boolean upper,StairGeometry.Shape shape,int settings){return geometry(facing,upper,shape,settings).bounds;}
    public static VoxelShape shape(Direction facing,boolean upper,StairGeometry.Shape shape,int settings){return geometry(facing,upper,shape,settings).shape;}
    public static boolean solidFace(Direction facing,boolean upper,StairGeometry.Shape shape,int settings,Direction side){return (geometry(facing,upper,shape,settings).solidFaces&(1<<side.get3DDataValue()))!=0;}
    private static Geometry geometry(Direction facing,boolean upper,StairGeometry.Shape shape,int settings){
        int tread=settings&31,roll=settings>>5;
        if(facing.getAxis()==Direction.Axis.Y||shape==null||!StairState.valid(tread,roll))throw new IllegalArgumentException("Invalid stair pose");
        int treadIndex=Integer.numberOfTrailingZeros(tread);
        int key=(((facing.get3DDataValue()-2)*2+(upper?0:1))*5+shape.ordinal())*15+treadIndex*3+roll;
        Geometry cached=CACHE.get(key);if(cached!=null)return cached;
        synchronized(CACHE_LOCK){cached=CACHE.get(key);if(cached!=null)return cached;
        List<AABB> boxes=List.copyOf(StairGeometry.boxes(facing.get3DDataValue(),upper,shape,settings,POINTS,BOXES));
        AABB bounds=boxes.getFirst();for(AABB box:boxes)bounds=bounds.minmax(box);
        int solid=0;for(Direction side:Direction.values())if(StairGeometry.solidFace(facing.get3DDataValue(),upper,shape,settings,side.get3DDataValue(),POINTS,BOXES))solid|=1<<side.get3DDataValue();
        VoxelShape union=NativeVoxelShapes.union(boxes,CACHE_BUDGET-CACHE_OVERHEAD);int cost=NativeVoxelShapes.retainedCost(union,boxes.size());
        Geometry value=new Geometry(boxes,bounds,union,solid,cost);
        while(cacheBytes+cost>CACHE_BUDGET){Geometry removed=CACHE.getAndSet(nextVictim,null);nextVictim=(nextVictim+1)%CACHE.length();if(removed!=null){cacheBytes-=removed.retainedCost;cacheSize--;}}
        CACHE.set(key,value);cacheBytes+=cost;cacheSize++;return value;
        }
    }
    static long cacheCost(){synchronized(CACHE_LOCK){return cacheBytes;}}
    static long cachedPoses(){synchronized(CACHE_LOCK){return cacheSize;}}
    static void clearCache(){synchronized(CACHE_LOCK){for(int i=0;i<CACHE.length();i++)CACHE.set(i,null);cacheBytes=CACHE_OVERHEAD;cacheSize=nextVictim=0;}}
    private NativeStairGeometry(){}
}
