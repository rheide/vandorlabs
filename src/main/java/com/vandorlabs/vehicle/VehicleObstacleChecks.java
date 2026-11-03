package com.vandorlabs.vehicle;

import java.util.Random;
import net.minecraft.util.math.AxisAlignedBB;

/** Exact comparison against the translated-object path, including foreign virtual offsets. */
public final class VehicleObstacleChecks {
    public static void run(){
        boolean previous=VehicleCollision.legacyObstacleOffsets;Random random=new Random(812733);int compared=0;
        try{
            VehicleCollision.legacyObstacleOffsets=false;
            for(int n=0;n<10000;n++){
                double x=(n%3==0?30_000_000:100)*random.nextDouble()*(n%2==0?-1:1),y=n%8==0?-0D:random.nextDouble()*256,z=-x*.37;
                AxisAlignedBB source=new AxisAlignedBB(-2,-.5,-3,2,2,3);double angle=n%7==0?Math.PI/2:random.nextDouble()*Math.PI*2;
                VehicleCollision.OrientedBox box=new VehicleCollision.OrientedBox(source,.25,-.5,Math.cos(angle),Math.sin(angle));
                double bx=random.nextDouble()*10-5,by=random.nextDouble()*6-2,bz=random.nextDouble()*10-5;
                if(n%5==0){bx=box.broad.maxX;by=box.broad.minY;bz=box.broad.minZ;}
                AxisAlignedBB obstacle=new AxisAlignedBB(x+bx,y+by,z+bz,x+bx+.5,y+by+1,z+bz+.75);
                AxisAlignedBB relative=obstacle.offset(-x,-y,-z);
                if(box.intersects(relative)!=VehicleCollision.relativeIntersects(box,obstacle,x,y,z))throw new IllegalStateException("Scalar intersection differs case="+n);
                for(int axis=0;axis<3;axis++)for(double amount:new double[]{-.28,.28,-1E-8,1E-8,0}){
                    double expected=box.clip(relative,amount,axis),actual=VehicleCollision.relativeClip(box,obstacle,x,y,z,amount,axis);
                    if(Double.doubleToLongBits(expected)!=Double.doubleToLongBits(actual))throw new IllegalStateException("Scalar clipping differs case="+n+" axis="+axis+" actual="+actual+" expected="+expected);compared++;
                }
            }
            final int[] calls={0};AxisAlignedBB foreign=new AxisAlignedBB(1,0,0,2,1,1){@Override public AxisAlignedBB offset(double x,double y,double z){calls[0]++;return super.offset(x+.125,y,z);}};
            VehicleCollision.OrientedBox box=new VehicleCollision.OrientedBox(new AxisAlignedBB(0,0,0,1,1,1),0,0,1,0);
            double expected=box.clip(foreign.offset(0,0,0),.28,0);int before=calls[0];double actual=VehicleCollision.relativeClip(box,foreign,0,0,0,.28,0);
            if(calls[0]!=before+1||Double.doubleToLongBits(expected)!=Double.doubleToLongBits(actual))throw new IllegalStateException("Foreign offset override bypassed");
            boolean intersects=box.intersects(foreign.offset(0,0,0));before=calls[0];if(intersects!=VehicleCollision.relativeIntersects(box,foreign,0,0,0)||calls[0]!=before+1)throw new IllegalStateException("Foreign intersection offset override bypassed");
            System.out.println("[vandorlabs][reprolab] vehicle-obstacle-scalars PASS exact-clips="+compared+" intersections=10000 far-negative-coordinates contacts tiny-movement foreign-offset-overrides");
        }finally{VehicleCollision.legacyObstacleOffsets=previous;}
    }
    private VehicleObstacleChecks(){}
}
