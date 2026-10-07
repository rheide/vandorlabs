package com.vandorlabs.blocks;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Shared model and selection transform, measured outward from the supporting face. */
public final class SignalControlMount {
    public static boolean valid(int height,int tilt,int direction){return height>=0&&height<=2&&tilt>=0&&tilt<=3&&direction>=0&&direction<4;}
    private final Vec3d normal,tangent,origin;
    private final double extra,sin,cos,lift;
    public SignalControlMount(EnumFacing facing,int rotation,int height,int tilt,int direction,AxisAlignedBB original){
        normal=new Vec3d(facing.getDirectionVec());origin=new Vec3d(.5,.5,.5).subtract(normal.scale(.5));
        Vec3d forward=facing.getAxis()==EnumFacing.Axis.Y?new Vec3d(0,0,facing==EnumFacing.UP?1:-1):new Vec3d(0,1,0);
        Vec3d right=normal.crossProduct(forward);
        int turn=(direction+(facing==EnumFacing.UP?-rotation:facing==EnumFacing.DOWN?rotation:0))&3;
        tangent=turn==0?forward:turn==1?right:turn==2?forward.scale(-1):right.scale(-1);
        extra=height*2/16D;sin=Math.sin(Math.toRadians(tilt*5));cos=Math.cos(Math.toRadians(tilt*5));
        double maximum=0;
        for(Vec3d p:corners(original))maximum=Math.max(maximum,p.subtract(origin).dotProduct(tangent));
        lift=maximum*sin;
    }
    public Vec3d transform(Vec3d point){
        Vec3d p=point.subtract(origin);double depth=p.dotProduct(normal),along=p.dotProduct(tangent);
        double raised=depth+extra*Math.min(1,Math.max(0,depth*16));
        return point.add(normal.scale(raised*cos-along*sin+lift-depth)).add(tangent.scale(raised*sin+along*cos-along));
    }
    public AxisAlignedBB bounds(AxisAlignedBB box){
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(Vec3d p:corners(box)){Vec3d q=transform(p);double[] v={q.x,q.y,q.z};for(int i=0;i<3;i++){min[i]=Math.min(min[i],v[i]);max[i]=Math.max(max[i],v[i]);}}
        return new AxisAlignedBB(min[0],min[1],min[2],max[0],max[1],max[2]);
    }
    private static java.util.List<Vec3d> corners(AxisAlignedBB b){java.util.List<Vec3d> points=new java.util.ArrayList<>();for(double x:new double[]{b.minX,b.maxX})for(double y:new double[]{b.minY,b.maxY})for(double z:new double[]{b.minZ,b.maxZ})points.add(new Vec3d(x,y,z));return points;}
}
