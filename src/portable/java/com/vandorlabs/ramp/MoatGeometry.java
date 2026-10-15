package com.vandorlabs.ramp;

import java.util.ArrayList;
import java.util.List;

/** Simultaneous native tread motion from a tower into an ordinary stepped ramp. */
public final class MoatGeometry {
    private MoatGeometry(){}
    private static int dx(RampGeometry.Direction f){return f==RampGeometry.Direction.EAST?1:f==RampGeometry.Direction.WEST?-1:0;}
    private static int dz(RampGeometry.Direction f){return f==RampGeometry.Direction.SOUTH?1:f==RampGeometry.Direction.NORTH?-1:0;}
    private static double progress(double p,boolean fast){p=Math.max(0,Math.min(1,p));return fast?p:p*p*(3-2*p);}
    public static List<RampGeometry.Box> pieces(RampGeometry.Direction face,RampGeometry.Box shape,int pixels){
        if(!ControllerPlatform.validTreadPixels(pixels))throw new IllegalArgumentException("Invalid Moat tread size");
        List<RampGeometry.Box> result=new ArrayList<>();
        for(int step=0;step<ControllerPlatform.treadCount(pixels);step++){
            RampGeometry.Box footprint=RampGeometry.footprintPixels(face,step,pixels,shape.minY,shape.maxY);
            double x=Math.max(shape.minX,footprint.minX),X=Math.min(shape.maxX,footprint.maxX),z=Math.max(shape.minZ,footprint.minZ),Z=Math.min(shape.maxZ,footprint.maxZ);
            if(X-x>1e-8&&Z-z>1e-8)result.add(new RampGeometry.Box(x,shape.minY,z,X,shape.maxY,Z));
        }
        return result;
    }
    public static int step(RampGeometry.Direction face,RampGeometry.Box local,int pixels){
        double center=dx(face)!=0?(local.minX+local.maxX)/2:(local.minZ+local.maxZ)/2;
        if(dx(face)<0||dz(face)<0)center=1-center;
        return Math.max(0,Math.min(ControllerPlatform.treadCount(pixels)-1,(int)Math.floor(center*16/pixels)));
    }
    private static double offset(int row,int step,int length,int pixels,double height,int interpolation){
        return ControllerPlatform.offsetPixels(row,step,length,pixels,0,height,1,false,true,interpolation);
    }
    private static double[] shift(RampGeometry.Direction face,int baseY,int length,int pixels,double start,double end,double p,int interpolation,int sourceY,int step){
        int row=sourceY-baseY;double a=row*Math.sin(p*Math.PI/2)+offset(row,step,length,pixels,start,interpolation)*(1-p);
        return new double[]{dx(face)*a,-row*(1-Math.cos(p*Math.PI/2))+offset(row,step,length,pixels,end,interpolation)*p,dz(face)*a};
    }
    public static double[] point(RampGeometry.Direction face,int baseY,int length,int pixels,double start,double end,double pose,boolean fast,int interpolation,int sourceY,int step,double x,double y,double z){
        double[] d=shift(face,baseY,length,pixels,start,end,progress(pose,fast),interpolation,sourceY,step);return new double[]{x+d[0],y+d[1],z+d[2]};
    }
    public static RampGeometry.Box transform(RampGeometry.Direction face,int baseY,int length,int pixels,double start,double end,double pose,boolean fast,int interpolation,int sourceY,int step,RampGeometry.Box original){
        double[] d=shift(face,baseY,length,pixels,start,end,progress(pose,fast),interpolation,sourceY,step);
        return new RampGeometry.Box(original.minX+d[0],original.minY+d[1],original.minZ+d[2],original.maxX+d[0],original.maxY+d[1],original.maxZ+d[2]);
    }
    public static RampGeometry.Box sweep(RampGeometry.Direction face,int baseY,int length,int pixels,double start,double end,double from,double to,boolean fast,int interpolation,int sourceY,int step,RampGeometry.Box original){
        double a=Math.min(progress(from,fast),progress(to,fast)),b=Math.max(progress(from,fast),progress(to,fast));int row=sourceY-baseY;
        double skew=offset(row,step,length,pixels,start,interpolation),rise=offset(row,step,length,pixels,end,interpolation);
        List<Double> samples=new ArrayList<>();samples.add(a);samples.add(b);
        if(row>0){double ratio=skew/(row*Math.PI/2);if(ratio>=0&&ratio<=1){double p=Math.acos(ratio)/(Math.PI/2);if(p>a&&p<b)samples.add(p);}
            ratio=rise/(row*Math.PI/2);if(ratio>=0&&ratio<=1){double p=Math.asin(ratio)/(Math.PI/2);if(p>a&&p<b)samples.add(p);}}
        double x=Double.POSITIVE_INFINITY,y=x,z=x,X=-x,Y=X,Z=X;
        for(double p:samples){double[] d=shift(face,baseY,length,pixels,start,end,p,interpolation,sourceY,step);x=Math.min(x,original.minX+d[0]);y=Math.min(y,original.minY+d[1]);z=Math.min(z,original.minZ+d[2]);X=Math.max(X,original.maxX+d[0]);Y=Math.max(Y,original.maxY+d[1]);Z=Math.max(Z,original.maxZ+d[2]);}
        return new RampGeometry.Box(x,y,z,X,Y,Z);
    }
    public static List<RampGeometry.Box> boxes(RampGeometry.Direction face,int minAlong,int baseY,int length,int pixels,double low,double high,double start,double end,double pose,boolean fast,int x,int y,int z){return boxes(face,minAlong,baseY,length,pixels,low,high,start,end,pose,fast,0,x,y,z);}
    public static List<RampGeometry.Box> boxes(RampGeometry.Direction face,int minAlong,int baseY,int length,int pixels,double low,double high,double start,double end,double pose,boolean fast,int interpolation,int x,int y,int z){
        List<RampGeometry.Box> result=new ArrayList<>();for(RampGeometry.Box piece:pieces(face,new RampGeometry.Box(0,low,0,1,high,1),pixels))result.add(transform(face,baseY,length,pixels,start,end,pose,fast,interpolation,y,step(face,piece,pixels),world(piece,x,y,z)));return result;
    }
    public static List<RampGeometry.Box> sweeps(RampGeometry.Direction face,int baseY,int length,int pixels,double low,double high,double start,double end,double from,double to,boolean fast,int interpolation,int x,int y,int z){
        List<RampGeometry.Box> result=new ArrayList<>();for(RampGeometry.Box piece:pieces(face,new RampGeometry.Box(0,low,0,1,high,1),pixels))result.add(sweep(face,baseY,length,pixels,start,end,from,to,fast,interpolation,y,step(face,piece,pixels),world(piece,x,y,z)));return result;
    }
    public static RampGeometry.Box world(RampGeometry.Box local,int x,int y,int z){return new RampGeometry.Box(x+local.minX,y+local.minY,z+local.minZ,x+local.maxX,y+local.maxY,z+local.maxZ);}
}
