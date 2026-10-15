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
    private static RampGeometry.Box closed(RampGeometry.Direction face,RampGeometry.Box piece,RampGeometry.Box shape){
        boolean alongX=dx(face)!=0,reverse=dx(face)<0||dz(face)<0;double a=alongX?shape.minX:shape.minZ,A=alongX?shape.maxX:shape.maxZ,p=alongX?piece.minX:piece.minZ,P=alongX?piece.maxX:piece.maxZ;
        double first=reverse?(A-P)/(A-a):(p-a)/(A-a),last=reverse?(A-p)/(A-a):(P-a)/(A-a);
        return new RampGeometry.Box(shape.minX,shape.minY+(shape.maxY-shape.minY)*first,shape.minZ,shape.maxX,shape.minY+(shape.maxY-shape.minY)*last,shape.maxZ);
    }
    private static RampGeometry.Box blend(RampGeometry.Box a,RampGeometry.Box b,double p){return new RampGeometry.Box(a.minX+(b.minX-a.minX)*p,a.minY+(b.minY-a.minY)*p,a.minZ+(b.minZ-a.minZ)*p,a.maxX+(b.maxX-a.maxX)*p,a.maxY+(b.maxY-a.maxY)*p,a.maxZ+(b.maxZ-a.maxZ)*p);}
    private static RampGeometry.Box defaultShape(RampGeometry.Box piece,int sourceY){return new RampGeometry.Box(Math.floor(piece.minX+1e-8),piece.minY,Math.floor(piece.minZ+1e-8),Math.floor(piece.minX+1e-8)+1,piece.maxY,Math.floor(piece.minZ+1e-8)+1);}
    public static RampGeometry.Box storedSection(RampGeometry.Direction face,RampGeometry.Box piece,RampGeometry.Box shape){return closed(face,piece,shape);}
    public static RampGeometry.Box transform(RampGeometry.Direction face,int baseY,int length,int pixels,double start,double end,double pose,boolean fast,int interpolation,int sourceY,int step,RampGeometry.Box original){return transform(face,baseY,length,pixels,start,end,pose,fast,interpolation,sourceY,step,original,defaultShape(original,sourceY));}
    public static RampGeometry.Box transform(RampGeometry.Direction face,int baseY,int length,int pixels,double start,double end,double pose,boolean fast,int interpolation,int sourceY,int step,RampGeometry.Box original,RampGeometry.Box shape){return at(face,baseY,length,pixels,start,end,progress(pose,fast),interpolation,sourceY,step,original,closed(face,original,shape));}
    private static RampGeometry.Box at(RampGeometry.Direction face,int baseY,int length,int pixels,double start,double end,double p,int interpolation,int sourceY,int step,RampGeometry.Box original,RampGeometry.Box stored){
        RampGeometry.Box section=blend(stored,original,p);double[] d=shift(face,baseY,length,pixels,start,end,p,interpolation,sourceY,step);
        return new RampGeometry.Box(section.minX+d[0],section.minY+d[1],section.minZ+d[2],section.maxX+d[0],section.maxY+d[1],section.maxZ+d[2]);
    }
    public static RampGeometry.Box sweep(RampGeometry.Direction face,int baseY,int length,int pixels,double start,double end,double from,double to,boolean fast,int interpolation,int sourceY,int step,RampGeometry.Box original){return sweep(face,baseY,length,pixels,start,end,from,to,fast,interpolation,sourceY,step,original,defaultShape(original,sourceY));}
    public static RampGeometry.Box sweep(RampGeometry.Direction face,int baseY,int length,int pixels,double start,double end,double from,double to,boolean fast,int interpolation,int sourceY,int step,RampGeometry.Box original,RampGeometry.Box shape){
        double a=Math.min(progress(from,fast),progress(to,fast)),b=Math.max(progress(from,fast),progress(to,fast));int row=sourceY-baseY;RampGeometry.Box stored=closed(face,original,shape);
        double skew=offset(row,step,length,pixels,start,interpolation),rise=offset(row,step,length,pixels,end,interpolation);List<Double> samples=new ArrayList<>();samples.add(a);samples.add(b);
        double[] initial={stored.minX,stored.minY,stored.minZ,stored.maxX,stored.maxY,stored.maxZ},target={original.minX,original.minY,original.minZ,original.maxX,original.maxY,original.maxZ};
        if(row>0)for(int bound=0;bound<6;bound++){int axis=bound%3;double linear=target[bound]-initial[bound],ratio;
            if(axis==1){ratio=(linear+rise)/(row*Math.PI/2);if(ratio>=0&&ratio<=1){double p=Math.asin(ratio)/(Math.PI/2);if(p>a&&p<b)samples.add(p);}}
            else{int direction=axis==0?dx(face):dz(face);if(direction!=0){ratio=(skew-linear*direction)/(row*Math.PI/2);if(ratio>=0&&ratio<=1){double p=Math.acos(ratio)/(Math.PI/2);if(p>a&&p<b)samples.add(p);}}}
        }
        double x=Double.POSITIVE_INFINITY,y=x,z=x,X=-x,Y=X,Z=X;
        for(double p:samples){RampGeometry.Box box=at(face,baseY,length,pixels,start,end,p,interpolation,sourceY,step,original,stored);x=Math.min(x,box.minX);y=Math.min(y,box.minY);z=Math.min(z,box.minZ);X=Math.max(X,box.maxX);Y=Math.max(Y,box.maxY);Z=Math.max(Z,box.maxZ);}
        return new RampGeometry.Box(x,y,z,X,Y,Z);
    }
    public static List<RampGeometry.Box> boxes(RampGeometry.Direction face,int minAlong,int baseY,int length,int pixels,double low,double high,double start,double end,double pose,boolean fast,int x,int y,int z){return boxes(face,minAlong,baseY,length,pixels,low,high,start,end,pose,fast,0,x,y,z);}
    public static List<RampGeometry.Box> boxes(RampGeometry.Direction face,int minAlong,int baseY,int length,int pixels,double low,double high,double start,double end,double pose,boolean fast,int interpolation,int x,int y,int z){
        List<RampGeometry.Box> result=new ArrayList<>();for(RampGeometry.Box piece:pieces(face,new RampGeometry.Box(0,low,0,1,high,1),pixels))result.add(transform(face,baseY,length,pixels,start,end,pose,fast,interpolation,y,step(face,piece,pixels),world(piece,x,y,z),new RampGeometry.Box(x,y+low,z,x+1,y+high,z+1)));return result;
    }
    public static List<RampGeometry.Box> sweeps(RampGeometry.Direction face,int baseY,int length,int pixels,double low,double high,double start,double end,double from,double to,boolean fast,int interpolation,int x,int y,int z){
        List<RampGeometry.Box> result=new ArrayList<>();for(RampGeometry.Box piece:pieces(face,new RampGeometry.Box(0,low,0,1,high,1),pixels))result.add(sweep(face,baseY,length,pixels,start,end,from,to,fast,interpolation,y,step(face,piece,pixels),world(piece,x,y,z),new RampGeometry.Box(x,y+low,z,x+1,y+high,z+1)));return result;
    }
    public static RampGeometry.Box world(RampGeometry.Box local,int x,int y,int z){return new RampGeometry.Box(x+local.minX,y+local.minY,z+local.minZ,x+local.maxX,y+local.maxY,z+local.maxZ);}
}
