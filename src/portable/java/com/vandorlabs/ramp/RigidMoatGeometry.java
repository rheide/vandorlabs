package com.vandorlabs.ramp;

import java.util.ArrayList;
import java.util.List;

/** Rigid drawbridge motion from a vertical source tower about its near bottom edge. */
public final class RigidMoatGeometry {
    private RigidMoatGeometry(){}
    private static int dx(RampGeometry.Direction f){return f==RampGeometry.Direction.EAST?1:f==RampGeometry.Direction.WEST?-1:0;}
    private static int dz(RampGeometry.Direction f){return f==RampGeometry.Direction.SOUTH?1:f==RampGeometry.Direction.NORTH?-1:0;}
    private static double progress(double p,boolean fast){p=Math.max(0,Math.min(1,p));return fast?p:p*p*(3-2*p);}
    private static double hinge(RampGeometry.Direction face,int minAlong){return minAlong-(dx(face)<0||dz(face)<0?1:0);}
    public static double angle(int length,double start,double end,double pose,boolean fast){
        double first=Math.atan2(start,Math.max(1,length)),last=Math.PI/2-Math.atan2(end,Math.max(1,length));
        return first+(last-first)*progress(pose,fast);
    }
    private static double[] rotated(RampGeometry.Direction face,int minAlong,int baseY,double angle,double x,double y,double z){
        int fx=dx(face),fz=dz(face);double u=x*fx+z*fz-hinge(face,minAlong),v=y-baseY;
        double along=u*Math.cos(angle)+v*Math.sin(angle),height=-u*Math.sin(angle)+v*Math.cos(angle),shift=along-u;
        return new double[]{x+fx*shift,baseY+height,z+fz*shift};
    }
    public static double[] vertex(RampGeometry.Direction face,int minAlong,int baseY,int length,double start,double end,double pose,boolean fast,double x,double y,double z){
        return rotated(face,minAlong,baseY,angle(length,start,end,pose,fast),x,y,z);
    }
    public static double[] inverse(RampGeometry.Direction face,int minAlong,int baseY,int length,double start,double end,double pose,boolean fast,double x,double y,double z){
        int fx=dx(face),fz=dz(face);double u=x*fx+z*fz-hinge(face,minAlong),v=y-baseY,a=angle(length,start,end,pose,fast);
        double original=u*Math.cos(a)-v*Math.sin(a),height=u*Math.sin(a)+v*Math.cos(a);
        return new double[]{x+fx*(original-u),baseY+height,z+fz*(original-u)};
    }
    private static void include(double[] bounds,double[] point){for(int axis=0;axis<3;axis++){bounds[axis]=Math.min(bounds[axis],point[axis]);bounds[axis+3]=Math.max(bounds[axis+3],point[axis]);}}
    private static double[] bounds(){return new double[]{Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};}
    private static RampGeometry.Box box(double[] b){return new RampGeometry.Box(b[0],b[1],b[2],b[3],b[4],b[5]);}
    public static RampGeometry.Box box(RampGeometry.Direction face,int minAlong,int baseY,int length,double start,double end,double pose,boolean fast,RampGeometry.Box original){
        double[] b=bounds();double a=angle(length,start,end,pose,fast);
        for(int ix=0;ix<2;ix++)for(int iy=0;iy<2;iy++)for(int iz=0;iz<2;iz++)include(b,rotated(face,minAlong,baseY,a,ix==0?original.minX:original.maxX,iy==0?original.minY:original.maxY,iz==0?original.minZ:original.maxZ));
        return box(b);
    }
    /** Slice source Y, so tread size controls chunks without bending the straight bridge. */
    public static List<RampGeometry.Box> slices(RampGeometry.Box source,int pixels){
        if(!ControllerPlatform.validTreadPixels(pixels))throw new IllegalArgumentException("Invalid Moat tread size");
        List<RampGeometry.Box> result=new ArrayList<>();double pitch=pixels/16D;
        for(int step=(int)Math.floor(source.minY/pitch);step<(int)Math.ceil(source.maxY/pitch);step++){
            double low=Math.max(source.minY,step*pitch),high=Math.min(source.maxY,(step+1)*pitch);
            if(high-low>1e-8)result.add(new RampGeometry.Box(source.minX,low,source.minZ,source.maxX,high,source.maxZ));
        }
        return result;
    }
    public static List<RampGeometry.Box> boxes(RampGeometry.Direction face,int minAlong,int baseY,int length,int pixels,double low,double high,double start,double end,double pose,boolean fast,int x,int y,int z){
        List<RampGeometry.Box> result=new ArrayList<>();
        for(RampGeometry.Box piece:slices(new RampGeometry.Box(x,y+low,z,x+1,y+high,z+1),pixels))result.add(box(face,minAlong,baseY,length,start,end,pose,fast,piece));
        return result;
    }
    /** Exact angular extrema of every corner; no padding can invade the fixed hinge. */
    public static RampGeometry.Box swept(RampGeometry.Direction face,int minAlong,int baseY,int length,int pixels,double low,double high,double start,double end,double from,double to,boolean fast,int x,int y,int z){
        if(!ControllerPlatform.validTreadPixels(pixels))throw new IllegalArgumentException("Invalid Moat tread size");
        double first=angle(length,start,end,from,fast),last=angle(length,start,end,to,fast),a=Math.min(first,last),A=Math.max(first,last);double[] result=bounds();
        for(int ix=0;ix<2;ix++)for(int iy=0;iy<2;iy++)for(int iz=0;iz<2;iz++){
            double px=x+ix,py=y+(iy==0?low:high),pz=z+iz,u=px*dx(face)+pz*dz(face)-hinge(face,minAlong),v=py-baseY;
            include(result,rotated(face,minAlong,baseY,first,px,py,pz));include(result,rotated(face,minAlong,baseY,last,px,py,pz));
            for(double extremum:new double[]{Math.atan2(v,u),Math.atan2(-u,v)})for(int k=(int)Math.ceil((a-extremum)/Math.PI);k<=(int)Math.floor((A-extremum)/Math.PI);k++)include(result,rotated(face,minAlong,baseY,extremum+k*Math.PI,px,py,pz));
        }
        return box(result);
    }
}
