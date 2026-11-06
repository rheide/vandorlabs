package com.vandorlabs.render;

/** Exact 1.12 AABB plane ordering, near-parallel cutoff and inclusive edge tests. */
public final class LegacyBoxTrace {
    private LegacyBoxTrace() {}
    public static final class Hit {
        public final double x,y,z; public final int face;
        public Hit(double x,double y,double z,int face){this.x=x;this.y=y;this.z=z;this.face=face;}
        public double distance(double x,double y,double z){double dx=this.x-x,dy=this.y-y,dz=this.z-z;return dx*dx+dy*dy+dz*dz;}
    }
    public static Hit clip(double minX,double minY,double minZ,double maxX,double maxY,double maxZ,double sx,double sy,double sz,double ex,double ey,double ez){
        Hit best=null;
        for(int plane=0;plane<6;plane++){
            int axis=plane/2;double delta=axis==0?ex-sx:axis==1?ey-sy:ez-sz;
            if(delta*delta<1.0000000116860974E-7D)continue;
            double value=axis==0?(plane%2==0?minX:maxX):axis==1?(plane%2==0?minY:maxY):(plane%2==0?minZ:maxZ);
            double t=(value-(axis==0?sx:axis==1?sy:sz))/delta;if(t<0||t>1)continue;
            double x=sx+(ex-sx)*t,y=sy+(ey-sy)*t,z=sz+(ez-sz)*t;
            if(axis==0?(y<minY||y>maxY||z<minZ||z>maxZ):axis==1?(x<minX||x>maxX||z<minZ||z>maxZ):(x<minX||x>maxX||y<minY||y>maxY))continue;
            int face=plane<2?4+plane:plane-2;
            Hit hit=new Hit(x,y,z,face);if(best==null||hit.distance(sx,sy,sz)<best.distance(sx,sy,sz))best=hit;
        }return best;
    }
}
