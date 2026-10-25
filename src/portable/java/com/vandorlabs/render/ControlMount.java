package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

/** Exact supporting-face pedestal/tilt transform shared by authored control models and collision. */
public final class ControlMount {
    public static final class Point {
        public final double x,y,z;
        public Point(double x,double y,double z){this.x=x==0?0:x;this.y=y==0?0:y;this.z=z==0?0:z;}
        public Point add(Point p){return new Point(x+p.x,y+p.y,z+p.z);}public Point subtract(Point p){return new Point(x-p.x,y-p.y,z-p.z);}public Point scale(double n){return new Point(x*n,y*n,z*n);}
        public double dot(Point p){return x*p.x+y*p.y+z*p.z;}public Point cross(Point p){return new Point(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
    }
    public static final class Box {
        public final double minX,minY,minZ,maxX,maxY,maxZ;
        public Box(double x0,double y0,double z0,double x1,double y1,double z1){minX=x0;minY=y0;minZ=z0;maxX=x1;maxY=y1;maxZ=z1;}
    }
    public static boolean valid(int height,int tilt,int direction){return height>=0&&height<=3&&tilt>=0&&tilt<=3&&direction>=0&&direction<4;}
    public static int migratedTilt(int tilt,boolean versioned){return !versioned&&tilt>0&&tilt<=3?1:tilt;}
    private final Point normal,tangent,origin;
    private final double extra,sin,cos,lift;
    public ControlMount(int facing,int rotation,int height,int tilt,int direction,Box original){
        normal=normal(facing);origin=new Point(.5,.5,.5).subtract(normal.scale(.5));
        Point forward=facing<2?new Point(0,0,facing==1?1:-1):new Point(0,1,0),right=normal.cross(forward);
        int turn=(direction+(facing==1?-rotation:facing==0?rotation:0))&3;
        tangent=turn==0?forward:turn==1?right:turn==2?forward.scale(-1):right.scale(-1);
        extra=height*2/16D;sin=Math.sin(Math.toRadians(tilt*15));cos=Math.cos(Math.toRadians(tilt*15));
        double maximum=0;for(Point p:corners(original))maximum=Math.max(maximum,p.subtract(origin).dot(tangent));lift=maximum*sin;
    }
    public static Point normal(int face){switch(face){case 0:return new Point(0,-1,0);case 1:return new Point(0,1,0);case 2:return new Point(0,0,-1);case 3:return new Point(0,0,1);case 4:return new Point(-1,0,0);case 5:return new Point(1,0,0);default:throw new IllegalArgumentException("Facing index");}}
    public Point transform(Point point){Point p=point.subtract(origin);double depth=p.dot(normal),along=p.dot(tangent);return point.add(normal.scale(depth*cos-along*sin+lift+extra-depth)).add(tangent.scale(depth*sin+along*cos-along));}
    public double depth(Point point){return point.subtract(origin).dot(normal);}
    public Point project(Point point){return point.subtract(normal.scale(depth(point)));}
    public Box bounds(Box box){List<Point> points=new ArrayList<>();for(Point p:corners(box))points.add(transform(p));for(Point p:corners(box))if(Math.abs(depth(p))<1e-7)points.add(project(transform(p)));return hull(points);}
    public List<Box> wedgeBoxes(Box support){
        List<Box> result=new ArrayList<>();int axis=Math.abs(tangent.x)>.5?0:Math.abs(tangent.y)>.5?1:2;double[] low={support.minX,support.minY,support.minZ},high={support.maxX,support.maxY,support.maxZ};
        int strips=Math.max(1,(int)Math.ceil((high[axis]-low[axis])*16));
        for(int i=0;i<strips;i++){double[] a=low.clone(),b=high.clone();a[axis]=low[axis]+(high[axis]-low[axis])*i/strips;b[axis]=low[axis]+(high[axis]-low[axis])*(i+1)/strips;List<Point> points=new ArrayList<>();for(Point p:corners(new Box(a[0],a[1],a[2],b[0],b[1],b[2]))){Point q=transform(p);points.add(q);points.add(project(q));}Box box=hull(points);if((box.maxX-box.minX+box.maxY-box.minY+box.maxZ-box.minZ)/3>1e-8)result.add(box);}return result;
    }
    public static List<Point> corners(Box box){List<Point> points=new ArrayList<>();for(double x:new double[]{box.minX,box.maxX})for(double y:new double[]{box.minY,box.maxY})for(double z:new double[]{box.minZ,box.maxZ})points.add(new Point(x,y,z));return points;}
    public static Box hull(List<Point> points){double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;for(Point p:points){x0=Math.min(x0,p.x);y0=Math.min(y0,p.y);z0=Math.min(z0,p.z);x1=Math.max(x1,p.x);y1=Math.max(y1,p.y);z1=Math.max(z1,p.z);}return new Box(x0,y0,z0,x1,y1,z1);}
    public static Point rotate(Point point,Point origin,int axis,double degrees){double[] p={point.x-origin.x,point.y-origin.y,point.z-origin.z};int a=(axis+1)%3,b=(axis+2)%3;double t=Math.toRadians(degrees),u=p[a],v=p[b];p[a]=u*Math.cos(t)-v*Math.sin(t);p[b]=u*Math.sin(t)+v*Math.cos(t);return new Point(p[0]+origin.x,p[1]+origin.y,p[2]+origin.z);}
}
