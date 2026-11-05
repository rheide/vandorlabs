package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

/** Current stepped height field; adapters construct their native points, boxes and emitted quads directly. */
public final class StairGeometry {
    public enum Shape {STRAIGHT,INNER_LEFT,INNER_RIGHT,OUTER_LEFT,OUTER_RIGHT}
    public interface Points<P> {P create(double x,double y,double z);double x(P p);double y(P p);double z(P p);}
    public interface Boxes<B> {B create(double minX,double minY,double minZ,double maxX,double maxY,double maxZ);double minX(B b);double minY(B b);double minZ(B b);double maxX(B b);double maxY(B b);double maxZ(B b);}
    public interface Faces<P,Q> {Q create(P a,P b,P c,P d,P textureA,P textureB,P textureC,P textureD,int face,int textureFace);}
    private StairGeometry(){}
    public static <P> P transform(double x,double y,double z,int facing,boolean upper,int roll,Points<P> points){
        y=upper?1-y:y;if(roll==1){double old=y;y=z;z=1-old;}else if(roll==2){double old=y;y=1-z;z=old;}
        switch(facing){case 4:return points.create(1-x,y,1-z);case 2:return points.create(z,y,1-x);case 3:return points.create(1-z,y,x);default:return points.create(x,y,z);}
    }
    public static double height(int x,int z,int count,Shape shape){double main=(x+1D)/count;if(shape==Shape.STRAIGHT)return main;double side=shape==Shape.INNER_LEFT||shape==Shape.OUTER_LEFT?(count-z)/(double)count:(z+1D)/count;return shape==Shape.INNER_LEFT||shape==Shape.INNER_RIGHT?Math.max(main,side):Math.min(main,side);}
    private static int direction(double x,double y,double z){double ax=Math.abs(x),ay=Math.abs(y),az=Math.abs(z);return ay>=ax&&ay>=az?(y>0?1:0):ax>=az?(x>0?5:4):(z>0?3:2);}
    public static <P> int unrolledFace(int facing,boolean upper,int roll,int face,Points<P> points){
        if(roll==0)return face;
        for(int local=0;local<6;local++){double x=.5+(local==4?-.5:local==5?.5:0),y=.5+(local==0?-.5:local==1?.5:0),z=.5+(local==2?-.5:local==3?.5:0);P mounted=transform(x,y,z,facing,upper,roll,points);if(direction(points.x(mounted)-.5,points.y(mounted)-.5,points.z(mounted)-.5)==face){P normal=transform(x,y,z,facing,upper,0,points);return direction(points.x(normal)-.5,points.y(normal)-.5,points.z(normal)-.5);}}
        throw new IllegalArgumentException("Invalid stair face");
    }
    public static <P,B> List<B> boxes(int facing,boolean upper,Shape shape,int settings,Points<P> points,Boxes<B> boxes){int count=16/(settings&31),roll=settings>>5;List<B> result=new ArrayList<>();for(int x=0;x<count;x++)for(int z=0;z<count;z++){P a=transform(x/(double)count,0,z/(double)count,facing,upper,roll,points),b=transform((x+1D)/count,height(x,z,count,shape),(z+1D)/count,facing,upper,roll,points);result.add(boxes.create(Math.min(points.x(a),points.x(b)),Math.min(points.y(a),points.y(b)),Math.min(points.z(a),points.z(b)),Math.max(points.x(a),points.x(b)),Math.max(points.y(a),points.y(b)),Math.max(points.z(a),points.z(b))));}return result;}
    public static <P,B> boolean solidFace(int facing,boolean upper,Shape shape,int settings,int side,Points<P> points,Boxes<B> boxes){List<B> solid=boxes(facing,upper,shape,settings,points,boxes);for(int a=0;a<16;a++)for(int b=0;b<16;b++){double edge=(side&1)==1?1-1e-6:1e-6,u=(a+.5)/16,v=(b+.5)/16,x=side>=4?edge:u,y=side>=4?u:side<2?edge:v,z=side<2?v:side>=4?v:edge;boolean filled=false;for(B box:solid)if(x>boxes.minX(box)&&x<boxes.maxX(box)&&y>boxes.minY(box)&&y<boxes.maxY(box)&&z>boxes.minZ(box)&&z<boxes.maxZ(box)){filled=true;break;}if(!filled)return false;}return true;}
    /** Emit the same exposed rectangles in source order, without intermediate portable point/face wrappers. */
    public static <P,Q> List<Q> mesh(int facing,boolean upper,Shape shape,int settings,Points<P> points,Faces<P,Q> faces){int count=16/(settings&31),roll=settings>>5;List<Q> result=new ArrayList<>();for(int x=0;x<count;x++)for(int z=0;z<count;z++){
        double x0=x/(double)count,x1=(x+1D)/count,z0=z/(double)count,z1=(z+1D)/count,h=height(x,z,count,shape);
        face(result,facing,upper,roll,points,faces,x0,h,z0,x0,h,z1,x1,h,z1,x1,h,z0);
        face(result,facing,upper,roll,points,faces,x0,0,z0,x1,0,z0,x1,0,z1,x0,0,z1);
        double left=x==0?0:height(x-1,z,count,shape),right=x==count-1?0:height(x+1,z,count,shape),north=z==0?0:height(x,z-1,count,shape),south=z==count-1?0:height(x,z+1,count,shape);
        if(h>left)face(result,facing,upper,roll,points,faces,x0,h,z0,x0,left,z0,x0,left,z1,x0,h,z1);
        if(h>right)face(result,facing,upper,roll,points,faces,x1,h,z0,x1,h,z1,x1,right,z1,x1,right,z0);
        if(h>north)face(result,facing,upper,roll,points,faces,x0,h,z0,x1,h,z0,x1,north,z0,x0,north,z0);
        if(h>south)face(result,facing,upper,roll,points,faces,x0,h,z1,x0,south,z1,x1,south,z1,x1,h,z1);
    }return result;}
    private static <P> int normal(P a,P b,P c,Points<P> points){double ux=points.x(b)-points.x(a),uy=points.y(b)-points.y(a),uz=points.z(b)-points.z(a),vx=points.x(c)-points.x(a),vy=points.y(c)-points.y(a),vz=points.z(c)-points.z(a);return direction(uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx);}
    private static <P,Q> void face(List<Q> out,int facing,boolean upper,int roll,Points<P> points,Faces<P,Q> faces,double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,double dx,double dy,double dz){
        P a=transform(ax,ay,az,facing,upper,roll,points),b=transform(bx,by,bz,facing,upper,roll,points),c=transform(cx,cy,cz,facing,upper,roll,points),d=transform(dx,dy,dz,facing,upper,roll,points),ta=transform(ax,ay,az,facing,upper,0,points),tb=transform(bx,by,bz,facing,upper,0,points),tc=transform(cx,cy,cz,facing,upper,0,points),td=transform(dx,dy,dz,facing,upper,0,points);
        if(upper){P swap=b;b=d;d=swap;swap=tb;tb=td;td=swap;}out.add(faces.create(a,b,c,d,ta,tb,tc,td,normal(a,b,c,points),normal(ta,tb,tc,points)));
    }
}
