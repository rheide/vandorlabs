package com.vandorlabs.canopy;

/** Scalar canopy point retaining the released vector's zero and float-sqrt normalization semantics. */
public final class CanopyPoint {
    public static final CanopyPoint ZERO=new CanopyPoint(0,0,0);
    public final double x,y,z;
    public CanopyPoint(double x,double y,double z){this.x=x==0?0:x;this.y=y==0?0:y;this.z=z==0?0:z;}
    public CanopyPoint add(CanopyPoint p){return addVector(p.x,p.y,p.z);}
    public CanopyPoint addVector(double x,double y,double z){return new CanopyPoint(this.x+x,this.y+y,this.z+z);}
    public CanopyPoint subtract(CanopyPoint p){return addVector(-p.x,-p.y,-p.z);}
    public CanopyPoint scale(double value){return new CanopyPoint(x*value,y*value,z*value);}
    public double lengthSquared(){return x*x+y*y+z*z;}
    public double dotProduct(CanopyPoint p){return x*p.x+y*p.y+z*p.z;}
    public CanopyPoint crossProduct(CanopyPoint p){return new CanopyPoint(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
    public CanopyPoint normalize(){double length=(float)Math.sqrt(lengthSquared());return length<1E-4?ZERO:new CanopyPoint(x/length,y/length,z/length);}
    public double squareDistanceTo(CanopyPoint p){double dx=p.x-x,dy=p.y-y,dz=p.z-z;return dx*dx+dy*dy+dz*dz;}
}
