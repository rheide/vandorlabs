package com.vandorlabs.physics;
/** Full 3D OBB separating-axis checks, including the nine cross-product axes. */
public final class OrientedBounds {
    public final double[] center,half;private final double[] rotation;
    public final double minX,minY,minZ,maxX,maxY,maxZ;
    public OrientedBounds(double x0,double y0,double z0,double x1,double y1,double z1,RigidTransform transform){
        center=transform.point((x0+x1)/2,(y0+y1)/2,(z0+z1)/2);half=new double[]{Math.max(0,(x1-x0)/2-1e-7),Math.max(0,(y1-y0)/2-1e-7),Math.max(0,(z1-z0)/2-1e-7)};
        double[] r=transform.r;rotation=r;
        double dx=Math.abs(r[0])*half[0]+Math.abs(r[1])*half[1]+Math.abs(r[2])*half[2],dy=Math.abs(r[3])*half[0]+Math.abs(r[4])*half[1]+Math.abs(r[5])*half[2],dz=Math.abs(r[6])*half[0]+Math.abs(r[7])*half[1]+Math.abs(r[8])*half[2];
        minX=center[0]-dx;minY=center[1]-dy;minZ=center[2]-dz;maxX=center[0]+dx;maxY=center[1]+dy;maxZ=center[2]+dz;
    }
    public boolean intersects(OrientedBounds b){
        if(maxX<=b.minX||minX>=b.maxX||maxY<=b.minY||minY>=b.maxY||maxZ<=b.minZ||minZ>=b.maxZ)return false;
        double dx=b.center[0]-center[0],dy=b.center[1]-center[1],dz=b.center[2]-center[2];
        for(int i=0;i<3;i++)if(separated(rotation[i],rotation[3+i],rotation[6+i],dx,dy,dz,b)||separated(b.rotation[i],b.rotation[3+i],b.rotation[6+i],dx,dy,dz,b))return false;
        for(int i=0;i<3;i++)for(int j=0;j<3;j++){
            double ax=rotation[i],ay=rotation[3+i],az=rotation[6+i],bx=b.rotation[j],by=b.rotation[3+j],bz=b.rotation[6+j];
            if(separated(ay*bz-az*by,az*bx-ax*bz,ax*by-ay*bx,dx,dy,dz,b))return false;
        }
        return true;
    }
    /** Continuous translation test over t in [0, 1], with both orientations fixed. */
    public boolean sweepsInto(OrientedBounds b,double vx,double vy,double vz){
        if(vx==0&&vy==0&&vz==0)return intersects(b);
        if(Math.max(maxX,maxX+vx)<=b.minX||Math.min(minX,minX+vx)>=b.maxX||
                Math.max(maxY,maxY+vy)<=b.minY||Math.min(minY,minY+vy)>=b.maxY||
                Math.max(maxZ,maxZ+vz)<=b.minZ||Math.min(minZ,minZ+vz)>=b.maxZ)return false;
        double dx=b.center[0]-center[0],dy=b.center[1]-center[1],dz=b.center[2]-center[2];
        double[] interval={0,1};
        for(int i=0;i<3;i++){
            if(!sweepAxis(rotation[i],rotation[3+i],rotation[6+i],dx,dy,dz,vx,vy,vz,b,interval)||
                    !sweepAxis(b.rotation[i],b.rotation[3+i],b.rotation[6+i],dx,dy,dz,vx,vy,vz,b,interval))return false;
        }
        for(int i=0;i<3;i++)for(int j=0;j<3;j++){
            double ax=rotation[i],ay=rotation[3+i],az=rotation[6+i],bx=b.rotation[j],by=b.rotation[3+j],bz=b.rotation[6+j];
            if(!sweepAxis(ay*bz-az*by,az*bx-ax*bz,ax*by-ay*bx,dx,dy,dz,vx,vy,vz,b,interval))return false;
        }
        return true;
    }
    private boolean sweepAxis(double x,double y,double z,double dx,double dy,double dz,double vx,double vy,double vz,OrientedBounds b,double[] interval){
        double length=x*x+y*y+z*z;
        if(length<1e-14)return true;
        double radius=projectedRadius(x,y,z,b)-1e-9*Math.sqrt(length);
        double difference=dx*x+dy*y+dz*z,speed=vx*x+vy*y+vz*z;
        if(Math.abs(speed)<1e-12)return Math.abs(difference)<radius;
        double first=(difference-radius)/speed,second=(difference+radius)/speed;
        interval[0]=Math.max(interval[0],Math.min(first,second));
        interval[1]=Math.min(interval[1],Math.max(first,second));
        return interval[0]<interval[1];
    }
    private boolean separated(double x,double y,double z,double dx,double dy,double dz,OrientedBounds b){
        double n=x*x+y*y+z*z;if(n<1e-14)return false;
        return Math.abs(dx*x+dy*y+dz*z)>=projectedRadius(x,y,z,b)-1e-9*Math.sqrt(n);
    }
    private double projectedRadius(double x,double y,double z,OrientedBounds b){
        double radius=0;
        for(int i=0;i<3;i++)radius+=half[i]*Math.abs(x*rotation[i]+y*rotation[3+i]+z*rotation[6+i])+b.half[i]*Math.abs(x*b.rotation[i]+y*b.rotation[3+i]+z*b.rotation[6+i]);
        return radius;
    }
}
