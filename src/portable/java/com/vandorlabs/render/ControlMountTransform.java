package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

/** Immutable scalar version of the released supporting-face pedestal and tilt transform. */
public class ControlMountTransform<V,B> {
    public interface PointFactory<V>{V create(double x,double y,double z);}
    public interface BoxFactory<B>{B create(double minX,double minY,double minZ,double maxX,double maxY,double maxZ);}
    private final PointFactory<V> points;
    private final BoxFactory<B> boxes;
    private final double nx,ny,nz,tx,ty,tz,ox,oy,oz,extra,sin,cos,lift;
    public ControlMountTransform(int face,int rotation,int height,int tilt,int direction,
            double minX,double minY,double minZ,double maxX,double maxY,double maxZ,
            PointFactory<V> points,BoxFactory<B> boxes){
        if(face<0||face>5)throw new IllegalArgumentException("Facing index");
        if(points==null||boxes==null)throw new NullPointerException("Geometry factory");
        this.points=points;this.boxes=boxes;
        nx=face==4?-1:face==5?1:0;ny=face==0?-1:face==1?1:0;nz=face==2?-1:face==3?1:0;
        ox=zero(.5-zero(nx*.5));oy=zero(.5-zero(ny*.5));oz=zero(.5-zero(nz*.5));
        double fx=0,fy=face<2?0:1,fz=face<2?(face==1?1:-1):0;
        double rx=zero(ny*fz-nz*fy),ry=zero(nz*fx-nx*fz),rz=zero(nx*fy-ny*fx);
        int turn=(direction+(face==1?-rotation:face==0?rotation:0))&3;
        tx=turn==0?fx:turn==1?rx:turn==2?zero(fx*-1):zero(rx*-1);
        ty=turn==0?fy:turn==1?ry:turn==2?zero(fy*-1):zero(ry*-1);
        tz=turn==0?fz:turn==1?rz:turn==2?zero(fz*-1):zero(rz*-1);
        extra=height*2/16D;sin=Math.sin(Math.toRadians(tilt*15));cos=Math.cos(Math.toRadians(tilt*15));
        double maximum=0;
        for(int corner=0;corner<8;corner++){
            double x=zero(zero((corner&4)==0?minX:maxX)-ox),y=zero(zero((corner&2)==0?minY:maxY)-oy),z=zero(zero((corner&1)==0?minZ:maxZ)-oz);
            maximum=Math.max(maximum,x*tx+y*ty+z*tz);
        }
        lift=maximum*sin;
    }
    // Vec3d normalizes signed zero at every vector-producing step. Keep those boundaries and operation order.
    private static double zero(double value){return value==0?0:value;}
    private <P> P transformed(double x,double y,double z,PointFactory<P> output){
        x=zero(x);y=zero(y);z=zero(z);
        double px=zero(x-ox),py=zero(y-oy),pz=zero(z-oz),depth=px*nx+py*ny+pz*nz,along=px*tx+py*ty+pz*tz;
        double normal=depth*cos-along*sin+lift+extra-depth,tangent=depth*sin+along*cos-along;
        double ax=zero(x+zero(nx*normal)),ay=zero(y+zero(ny*normal)),az=zero(z+zero(nz*normal));
        return output.create(zero(ax+zero(tx*tangent)),zero(ay+zero(ty*tangent)),zero(az+zero(tz*tangent)));
    }
    private <P> P projected(double x,double y,double z,PointFactory<P> output){
        x=zero(x);y=zero(y);z=zero(z);double depth=depth(x,y,z);
        return output.create(zero(x-zero(nx*depth)),zero(y-zero(ny*depth)),zero(z-zero(nz*depth)));
    }
    public V transform(double x,double y,double z){return transformed(x,y,z,points);}
    public V project(double x,double y,double z){return projected(x,y,z,points);}
    public double depth(double x,double y,double z){return zero(zero(x)-ox)*nx+zero(zero(y)-oy)*ny+zero(zero(z)-oz)*nz;}
    public B bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        Scratch point=new Scratch();Extents result=new Extents();
        for(int corner=0;corner<8;corner++)result.add(transformed((corner&4)==0?minX:maxX,(corner&2)==0?minY:maxY,(corner&1)==0?minZ:maxZ,point));
        for(int corner=0;corner<8;corner++){
            double x=(corner&4)==0?minX:maxX,y=(corner&2)==0?minY:maxY,z=(corner&1)==0?minZ:maxZ;
            if(Math.abs(depth(x,y,z))<1e-7){transformed(x,y,z,point);result.add(projected(point.x,point.y,point.z,point));}
        }
        return result.box(boxes);
    }
    public List<B> wedgeBoxes(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        List<B> result=new ArrayList<>();int axis=Math.abs(tx)>.5?0:Math.abs(ty)>.5?1:2;
        double low=axis==0?minX:axis==1?minY:minZ,high=axis==0?maxX:axis==1?maxY:maxZ;
        int strips=Math.max(1,(int)Math.ceil((high-low)*16));Scratch point=new Scratch();Extents extents=new Extents();
        for(int strip=0;strip<strips;strip++){
            double a=low+(high-low)*strip/strips,b=low+(high-low)*(strip+1)/strips;
            double ax=axis==0?a:minX,ay=axis==1?a:minY,az=axis==2?a:minZ,bx=axis==0?b:maxX,by=axis==1?b:maxY,bz=axis==2?b:maxZ;
            double x0=Math.min(ax,bx),y0=Math.min(ay,by),z0=Math.min(az,bz),x1=Math.max(ax,bx),y1=Math.max(ay,by),z1=Math.max(az,bz);extents.reset();
            for(int corner=0;corner<8;corner++){
                transformed((corner&4)==0?x0:x1,(corner&2)==0?y0:y1,(corner&1)==0?z0:z1,point);extents.add(point);extents.add(projected(point.x,point.y,point.z,point));
            }
            if(((extents.x1-extents.x0)+(extents.y1-extents.y0)+(extents.z1-extents.z0))/3D>1e-8)result.add(extents.box(boxes));
        }
        return result;
    }
    /** Call-local scratch only: never retained by a transform, cache or public result. */
    private static final class Scratch implements PointFactory<Scratch>{
        double x,y,z;public Scratch create(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
    }
    private static final class Extents {
        double x0,y0,z0,x1,y1,z1;Extents(){reset();}
        void reset(){x0=y0=z0=Double.POSITIVE_INFINITY;x1=y1=z1=Double.NEGATIVE_INFINITY;}
        void add(Scratch p){x0=Math.min(x0,p.x);y0=Math.min(y0,p.y);z0=Math.min(z0,p.z);x1=Math.max(x1,p.x);y1=Math.max(y1,p.y);z1=Math.max(z1,p.z);}
        <B> B box(BoxFactory<B> factory){return factory.create(x0,y0,z0,x1,y1,z1);}
    }
}
