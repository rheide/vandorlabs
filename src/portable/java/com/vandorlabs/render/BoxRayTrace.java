package com.vandorlabs.render;

/** Stable box-boundary picking, including exit faces when the ray starts inside a box. */
public final class BoxRayTrace {
    public static final class Hit {
        public final double x,y,z;
        public final int facing;
        private Hit(double x,double y,double z,int facing){this.x=x;this.y=y;this.z=z;this.facing=facing;}
    }
    private BoxRayTrace(){}
    public static Hit trace(DiagonalScreenHull.Bounds[] boxes,double sx,double sy,double sz,double ex,double ey,double ez){
        double dx=ex-sx,dy=ey-sy,dz=ez-sz,best=Double.POSITIVE_INFINITY;Hit hit=null;
        for(DiagonalScreenHull.Bounds box:boxes)for(int plane=0;plane<6;plane++){
            int axis=plane/2,side=(plane&1);double delta=axis==0?dx:axis==1?dy:dz;if(delta*delta<1.0000000116860974E-7)continue;
            double coordinate=axis==0?(side==0?box.minX:box.maxX):axis==1?(side==0?box.minY:box.maxY):(side==0?box.minZ:box.maxZ);
            double t=(coordinate-(axis==0?sx:axis==1?sy:sz))/delta;if(t<0||t>1)continue;
            double x=sx+dx*t,y=sy+dy*t,z=sz+dz*t;
            if(axis!=0&&(x<box.minX||x>box.maxX)||axis!=1&&(y<box.minY||y>box.maxY)||axis!=2&&(z<box.minZ||z>box.maxZ))continue;
            double distance=(x-sx)*(x-sx)+(y-sy)*(y-sy)+(z-sz)*(z-sz);
            if(distance<best){best=distance;hit=new Hit(x,y,z,axis==0?4+side:axis==1?side:2+side);}
        }
        return hit;
    }
}
