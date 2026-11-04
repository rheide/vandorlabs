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
    private boolean separated(double x,double y,double z,double dx,double dy,double dz,OrientedBounds b){
        double n=x*x+y*y+z*z;if(n<1e-14)return false;double radius=0;
        for(int i=0;i<3;i++)radius+=half[i]*Math.abs(x*rotation[i]+y*rotation[3+i]+z*rotation[6+i])+b.half[i]*Math.abs(x*b.rotation[i]+y*b.rotation[3+i]+z*b.rotation[6+i]);
        return Math.abs(dx*x+dy*y+dz*z)>=radius-1e-9*Math.sqrt(n);
    }
}
