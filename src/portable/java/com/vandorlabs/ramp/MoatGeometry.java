package com.vandorlabs.ramp;

import java.util.ArrayList;
import java.util.List;

/** A drawbridge rotates around the near top edge of the captured flat platform. */
public final class MoatGeometry {
    private MoatGeometry(){}
    private static int dx(RampGeometry.Direction f){return f==RampGeometry.Direction.EAST?1:f==RampGeometry.Direction.WEST?-1:0;}
    private static int dz(RampGeometry.Direction f){return f==RampGeometry.Direction.SOUTH?1:f==RampGeometry.Direction.NORTH?-1:0;}
    private static double progress(double p,boolean fast){p=Math.max(0,Math.min(1,p));return fast?p:p*p*(3-2*p);}
    public static double[] vertex(RampGeometry.Direction face,int minAlong,int sourceY,double high,double start,double end,double pose,boolean fast,double x,double y,double z){
        int fx=dx(face),fz=dz(face);double hinge=minAlong-(fx<0||fz<0?1:0),u=x*fx+z*fz-hinge,v=y-sourceY-high,p=progress(pose,fast),angle=(1-p)*Math.PI/2;
        double along=u*Math.cos(angle)-v*Math.sin(angle),vertical=u*Math.sin(angle)+v*Math.cos(angle)+sourceY+high+start+(end-start)*p;
        double shift=along-u;return new double[]{x+fx*shift,vertical,z+fz*shift};
    }
    public static RampGeometry.Box box(RampGeometry.Direction f,int minAlong,int sourceY,double high,double start,double end,double pose,boolean fast,RampGeometry.Box original){
        double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=-x0,y1=x1,z1=x1;
        for(int ix=0;ix<2;ix++)for(int iy=0;iy<2;iy++)for(int iz=0;iz<2;iz++){double[] v=vertex(f,minAlong,sourceY,high,start,end,pose,fast,ix==0?original.minX:original.maxX,iy==0?original.minY:original.maxY,iz==0?original.minZ:original.maxZ);x0=Math.min(x0,v[0]);y0=Math.min(y0,v[1]);z0=Math.min(z0,v[2]);x1=Math.max(x1,v[0]);y1=Math.max(y1,v[1]);z1=Math.max(z1,v[2]);}
        return new RampGeometry.Box(x0,y0,z0,x1,y1,z1);
    }
    public static double[] inverse(RampGeometry.Direction face,int minAlong,int sourceY,double high,double start,double end,double pose,boolean fast,double x,double y,double z){
        int fx=dx(face),fz=dz(face);double hinge=minAlong-(fx<0||fz<0?1:0),u=x*fx+z*fz-hinge,p=progress(pose,fast),angle=(1-p)*Math.PI/2,v=y-sourceY-high-start-(end-start)*p;
        double original=u*Math.cos(angle)+v*Math.sin(angle),height=-u*Math.sin(angle)+v*Math.cos(angle);return new double[]{x+fx*(original-u),sourceY+high+height,z+fz*(original-u)};
    }
    public static List<RampGeometry.Box> boxes(RampGeometry.Direction f,int minAlong,int sourceY,double low,double high,double start,double end,double pose,boolean fast,int x,int y,int z){
        List<RampGeometry.Box> result=new ArrayList<>();boolean alongX=dx(f)!=0;
        for(int i=0;i<16;i++){RampGeometry.Box original=new RampGeometry.Box(x+(alongX?i/16D:0),y+low,z+(alongX?0:i/16D),x+(alongX?(i+1)/16D:1),y+high,z+(alongX?1:(i+1)/16D));result.add(box(f,minAlong,sourceY,high,start,end,pose,fast,original));}return result;
    }
    /** Sample the arc and pad by its proven chord-error bound; endpoint unions miss the outer sweep. */
    public static RampGeometry.Box swept(RampGeometry.Direction f,int minAlong,int sourceY,double low,double high,double start,double end,double from,double to,boolean fast,int x,int y,int z){
        double first=progress(from,fast),last=progress(to,fast),span=Math.abs(last-first)*Math.PI/2;int steps=Math.max(1,(int)Math.ceil(span*64/(Math.PI/2)));
        double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=-x0,y1=x1,z1=x1;
        // Linear progress is the transform parameter; invert easing only at the public boundary.
        for(int i=0;i<=steps;i++){double p=first+(last-first)*i/steps;RampGeometry.Box b=box(f,minAlong,sourceY,high,start,end,p,true,new RampGeometry.Box(x,y+low,z,x+1,y+high,z+1));x0=Math.min(x0,b.minX);y0=Math.min(y0,b.minY);z0=Math.min(z0,b.minZ);x1=Math.max(x1,b.maxX);y1=Math.max(y1,b.maxY);z1=Math.max(z1,b.maxZ);}
        int fx=dx(f),fz=dz(f);double hinge=minAlong-(fx<0||fz<0?1:0),radius=Math.abs(x*fx+z*fz-hinge)+3,pad=radius*(span/steps)*(span/steps)/8;
        if(fx!=0){x0-=pad;x1+=pad;if(fx>0)x0=Math.max(x0,hinge);else x1=Math.min(x1,-hinge);}else{z0-=pad;z1+=pad;if(fz>0)z0=Math.max(z0,hinge);else z1=Math.min(z1,-hinge);}
        y0=Math.max(y0-pad,sourceY+low+Math.min(start,end));return new RampGeometry.Box(x0,y0,z0,x1,y1+pad,z1);
    }
}
