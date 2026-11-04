package com.vandorlabs.physics;
/** Full 3D OBB separating-axis checks, including the nine cross-product axes. */
final class ReferenceOrientedBounds {
    public final double[] center,half;private final double[][] axes;
    public final double minX,minY,minZ,maxX,maxY,maxZ;
    public ReferenceOrientedBounds(double x0,double y0,double z0,double x1,double y1,double z1,RigidTransform transform){
        center=transform.point((x0+x1)/2,(y0+y1)/2,(z0+z1)/2);half=new double[]{Math.max(0,(x1-x0)/2-1e-7),Math.max(0,(y1-y0)/2-1e-7),Math.max(0,(z1-z0)/2-1e-7)};
        double[] r=transform.r;axes=new double[][]{{r[0],r[3],r[6]},{r[1],r[4],r[7]},{r[2],r[5],r[8]}};
        double dx=Math.abs(r[0])*half[0]+Math.abs(r[1])*half[1]+Math.abs(r[2])*half[2],dy=Math.abs(r[3])*half[0]+Math.abs(r[4])*half[1]+Math.abs(r[5])*half[2],dz=Math.abs(r[6])*half[0]+Math.abs(r[7])*half[1]+Math.abs(r[8])*half[2];
        minX=center[0]-dx;minY=center[1]-dy;minZ=center[2]-dz;maxX=center[0]+dx;maxY=center[1]+dy;maxZ=center[2]+dz;
    }
    public boolean intersects(ReferenceOrientedBounds b){
        if(maxX<=b.minX||minX>=b.maxX||maxY<=b.minY||minY>=b.maxY||maxZ<=b.minZ||minZ>=b.maxZ)return false;
        double[] delta={b.center[0]-center[0],b.center[1]-center[1],b.center[2]-center[2]};
        for(int i=0;i<3;i++)if(separated(axes[i],delta,b)||separated(b.axes[i],delta,b))return false;
        for(int i=0;i<3;i++)for(int j=0;j<3;j++){double[] a=axes[i],c=b.axes[j],axis={a[1]*c[2]-a[2]*c[1],a[2]*c[0]-a[0]*c[2],a[0]*c[1]-a[1]*c[0]};if(separated(axis,delta,b))return false;}return true;
    }
    private boolean separated(double[] axis,double[] delta,ReferenceOrientedBounds b){double n=dot(axis,axis);if(n<1e-14)return false;double radius=0;for(int i=0;i<3;i++)radius+=half[i]*Math.abs(dot(axis,axes[i]))+b.half[i]*Math.abs(dot(axis,b.axes[i]));return Math.abs(dot(delta,axis))>=radius-1e-9*Math.sqrt(n);}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
}
