package com.vandorlabs.blocks;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Shared model and selection transform, measured outward from the supporting face. */
public final class SignalControlMount {
    public static boolean valid(int height,int tilt,int direction){return height>=0&&height<=3&&tilt>=0&&tilt<=3&&direction>=0&&direction<4;}
    private final Vec3d normal,tangent,origin;
    private final double extra,sin,cos,lift;
    public SignalControlMount(EnumFacing facing,int rotation,int height,int tilt,int direction,AxisAlignedBB original){
        normal=new Vec3d(facing.getDirectionVec());origin=new Vec3d(.5,.5,.5).subtract(normal.scale(.5));
        Vec3d forward=facing.getAxis()==EnumFacing.Axis.Y?new Vec3d(0,0,facing==EnumFacing.UP?1:-1):new Vec3d(0,1,0);
        Vec3d right=normal.crossProduct(forward);
        int turn=(direction+(facing==EnumFacing.UP?-rotation:facing==EnumFacing.DOWN?rotation:0))&3;
        tangent=turn==0?forward:turn==1?right:turn==2?forward.scale(-1):right.scale(-1);
        extra=height*2/16D;sin=Math.sin(Math.toRadians(tilt*15));cos=Math.cos(Math.toRadians(tilt*15));
        double maximum=0;
        for(Vec3d p:corners(original))maximum=Math.max(maximum,p.subtract(origin).dotProduct(tangent));
        lift=maximum*sin;
    }
    public Vec3d transform(Vec3d point){
        Vec3d p=point.subtract(origin);double depth=p.dotProduct(normal),along=p.dotProduct(tangent);
        // Height is an upright pedestal; rotate the unextended control above it.
        return point.add(normal.scale(depth*cos-along*sin+lift+extra-depth)).add(tangent.scale(depth*sin+along*cos-along));
    }
    public double depth(Vec3d point){return point.subtract(origin).dotProduct(normal);}
    public Vec3d project(Vec3d point){return point.subtract(normal.scale(depth(point)));}
    public AxisAlignedBB bounds(AxisAlignedBB box){
        java.util.List<Vec3d> points=new java.util.ArrayList<>();
        for(Vec3d p:corners(box))points.add(transform(p));
        // The filled base reaches the support plane even when the control tilts away from it.
        for(Vec3d p:corners(box))if(Math.abs(depth(p))<1e-7)points.add(project(transform(p)));
        return SignalControlShape.hull(points);
    }
    public java.util.List<AxisAlignedBB> wedgeBoxes(AxisAlignedBB support){
        java.util.List<AxisAlignedBB> boxes=new java.util.ArrayList<>();
        int axis=Math.abs(tangent.x)>.5?0:Math.abs(tangent.y)>.5?1:2;
        double[] low={support.minX,support.minY,support.minZ},high={support.maxX,support.maxY,support.maxZ};
        // One-pixel strips follow the slope without making the triangular air above it solid.
        int strips=Math.max(1,(int)Math.ceil((high[axis]-low[axis])*16));
        for(int i=0;i<strips;i++){
            double[] a=low.clone(),b=high.clone();a[axis]=low[axis]+(high[axis]-low[axis])*i/strips;b[axis]=low[axis]+(high[axis]-low[axis])*(i+1)/strips;
            java.util.List<Vec3d> points=new java.util.ArrayList<>();
            for(Vec3d p:corners(new AxisAlignedBB(a[0],a[1],a[2],b[0],b[1],b[2]))){Vec3d q=transform(p);points.add(q);points.add(project(q));}
            AxisAlignedBB box=SignalControlShape.hull(points);if(box.getAverageEdgeLength()>1e-8)boxes.add(box);
        }
        return boxes;
    }
    public static int readTilt(net.minecraft.nbt.NBTTagCompound tag){
        int tilt=tag.getInteger("BaseTilt");
        // Earlier presets were 5/10/15 degrees; non-flat legacy mounts use the first supported tilted option.
        return !tag.hasKey("ControlMountVersion") && tilt>0 && tilt<=3?1:tilt;
    }
    private static java.util.List<Vec3d> corners(AxisAlignedBB b){java.util.List<Vec3d> points=new java.util.ArrayList<>();for(double x:new double[]{b.minX,b.maxX})for(double y:new double[]{b.minY,b.maxY})for(double z:new double[]{b.minZ,b.maxZ})points.add(new Vec3d(x,y,z));return points;}
}
