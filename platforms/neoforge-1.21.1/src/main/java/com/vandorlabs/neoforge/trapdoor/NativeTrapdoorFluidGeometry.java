package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.fluid.NativeFluidVolume;
import com.vandorlabs.render.TrapdoorLeafPose;
import java.util.*;
import net.minecraft.world.phys.AABB;

/** Exact CLOSED separating plane and current endpoint's physical void; no world or slot ownership retained. */
public final class NativeTrapdoorFluidGeometry {
    private NativeTrapdoorFluidGeometry(){}
    public record Plane(double x,double y,double z,double near,double far) {
        public int side(double px,double py,double pz){return px*x+py*y+pz*z<(near+far)/2?0:1;}
        public double[][] cuts(){double[][] cuts=new double[3][];for(int axis=0;axis<3;axis++){double normal=axis==0?x:axis==1?y:z;List<Double> values=new ArrayList<>();values.add(.5);if(Math.abs(normal)>.999)for(double end:new double[]{near,far}){double cut=end/normal;if(cut>0&&cut<1)values.add(cut);}cuts[axis]=values.stream().mapToDouble(Double::doubleValue).toArray();}return cuts;}
    }
    public record Snapshot(Plane plane,NativeFluidVolume volume,int retainedBytes){}
    /** Released Vec3d normalizes signed zero and rounds sqrt through float; both affect partition bits. */
    public static Plane plane(TrapdoorLeafPose pose){
        double[][] points=new double[8][3];pose.write(0,true,points);double[][] edges=new double[3][3];for(int edge=0;edge<3;edge++)for(int axis=0;axis<3;axis++)edges[edge][axis]=zero(zero(points[1<<edge][axis])-zero(points[0][axis]));int thin=0;for(int edge=1;edge<3;edge++)if(lengthSquared(edges[edge])<lengthSquared(edges[thin]))thin=edge;var a=edges[(thin+1)%3];var b=edges[(thin+2)%3];double x=a[1]*b[2]-a[2]*b[1],y=a[2]*b[0]-a[0]*b[2],z=a[0]*b[1]-a[1]*b[0],length=(double)(float)Math.sqrt(x*x+y*y+z*z);if(length<1e-4){x=y=z=0;}else{x/=length;y/=length;z/=length;}var edge=edges[thin];if(x*edge[0]+y*edge[1]+z*edge[2]<0){x*=-1;y*=-1;z*=-1;}x=zero(x);y=zero(y);z=zero(z);double near=Double.POSITIVE_INFINITY,far=Double.NEGATIVE_INFINITY;for(var point:points){double dot=x*zero(point[0])+y*zero(point[1])+z*zero(point[2]);near=Math.min(near,dot);far=Math.max(far,dot);}return new Plane(x,y,z,near,far);
    }
    private static double zero(double value){return value==0?0:value;}
    private static double lengthSquared(double[] edge){return edge[0]*edge[0]+edge[1]*edge[1]+edge[2]*edge[2];}
    public static Snapshot build(TrapdoorLeafPose pose,boolean open,int x,int y,int z){
        var plane=plane(pose);var geometry=NativeTrapdoorGeometry.snapshot(pose,open);List<AABB> solids=new ArrayList<>();for(int index=0;index<geometry.collisionCount();index++){var local=geometry.collision(index);var world=local.move(x,y,z);if(world.maxX>x&&world.minX<x+1D&&world.maxY>y&&world.minY<y+1D&&world.maxZ>z&&world.minZ<z+1D)solids.add(new AABB(Math.max(0,world.minX-x),Math.max(0,world.minY-y),Math.max(0,world.minZ-z),Math.min(1,world.maxX-x),Math.min(1,world.maxY-y),Math.min(1,world.maxZ-z)));}
        var volume=NativeFluidVolume.build(solids,plane::side,plane.cuts());long retained=1024L+volume.cells().size()*112L+solids.size()*80L+solids.size()*6L*8;if(retained>16*1024*1024)throw new IllegalStateException("Trapdoor fluid snapshot exceeds checked retained budget");return new Snapshot(plane,volume,(int)retained);
    }
}
