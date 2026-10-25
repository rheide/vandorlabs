package com.vandorlabs.render;

import com.vandorlabs.render.ControlMount.Point;
import com.vandorlabs.render.ControlMount.Box;
import java.util.ArrayList;
import java.util.List;

/** The authored stair height field, exact cardinal rolls, exposed faces and unrolled UVs. */
public final class StairGeometry {
    public enum Shape {STRAIGHT,INNER_LEFT,INNER_RIGHT,OUTER_LEFT,OUTER_RIGHT}
    public static final class Face {
        public final Point[] vertices;public final double[] u,v;public final int face,textureFace;
        private Face(Point[] vertices,double[] u,double[] v,int face,int textureFace){this.vertices=vertices;this.u=u;this.v=v;this.face=face;this.textureFace=textureFace;}
    }
    private StairGeometry(){}
    public static Point transform(Point p,int facing,boolean upper,int roll){
        double x=p.x,y=upper?1-p.y:p.y,z=p.z;
        if(roll==1){double old=y;y=z;z=1-old;}else if(roll==2){double old=y;y=1-z;z=old;}
        switch(facing){case 4:return new Point(1-x,y,1-z);case 2:return new Point(z,y,1-x);case 3:return new Point(1-z,y,x);default:return new Point(x,y,z);}
    }
    public static double height(int x,int z,int count,Shape shape){
        double main=(x+1D)/count;if(shape==Shape.STRAIGHT)return main;
        double side=shape==Shape.INNER_LEFT||shape==Shape.OUTER_LEFT?(count-z)/(double)count:(z+1D)/count;
        return shape==Shape.INNER_LEFT||shape==Shape.INNER_RIGHT?Math.max(main,side):Math.min(main,side);
    }
    private static int direction(Point normal){double x=Math.abs(normal.x),y=Math.abs(normal.y),z=Math.abs(normal.z);return y>=x&&y>=z?(normal.y>0?1:0):x>=z?(normal.x>0?5:4):(normal.z>0?3:2);}
    public static int unrolledFace(int facing,boolean upper,int roll,int face){
        if(roll==0)return face;Point center=new Point(.5,.5,.5);
        for(int local=0;local<6;local++){Point normal=local==0?new Point(0,-1,0):local==1?new Point(0,1,0):local==2?new Point(0,0,-1):local==3?new Point(0,0,1):local==4?new Point(-1,0,0):new Point(1,0,0);Point point=center.add(normal.scale(.5));if(direction(transform(point,facing,upper,roll).subtract(center))==face)return direction(transform(point,facing,upper,0).subtract(center));}
        throw new IllegalArgumentException("Invalid stair face");
    }
    public static List<Box> boxes(int facing,boolean upper,Shape shape,int settings){
        int count=16/(settings&31),roll=settings>>5;List<Box> result=new ArrayList<>();
        for(int x=0;x<count;x++)for(int z=0;z<count;z++){
            Point a=transform(new Point(x/(double)count,0,z/(double)count),facing,upper,roll),b=transform(new Point((x+1D)/count,height(x,z,count,shape),(z+1D)/count),facing,upper,roll);
            result.add(new Box(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z)));
        }return result;
    }
    private static boolean contains(Box b,Point p){return p.x>b.minX&&p.x<b.maxX&&p.y>b.minY&&p.y<b.maxY&&p.z>b.minZ&&p.z<b.maxZ;}
    public static boolean solidFace(int facing,boolean upper,Shape shape,int settings,int side){
        List<Box> boxes=boxes(facing,upper,shape,settings);
        for(int a=0;a<16;a++)for(int b=0;b<16;b++){
            double edge=(side&1)==1?1-1e-6:1e-6,u=(a+.5)/16,v=(b+.5)/16;Point point=side>=4?new Point(edge,u,v):side<2?new Point(u,edge,v):new Point(u,v,edge);
            boolean filled=false;for(Box box:boxes)if(contains(box,point)){filled=true;break;}if(!filled)return false;
        }return true;
    }
    public static List<Face> mesh(int facing,boolean upper,Shape shape,int settings){
        int count=16/(settings&31),roll=settings>>5;List<Face> result=new ArrayList<>();
        for(int x=0;x<count;x++)for(int z=0;z<count;z++){
            double x0=x/(double)count,x1=(x+1D)/count,z0=z/(double)count,z1=(z+1D)/count,h=height(x,z,count,shape);
            face(result,facing,upper,roll,new double[][]{{x0,h,z0},{x0,h,z1},{x1,h,z1},{x1,h,z0}});
            face(result,facing,upper,roll,new double[][]{{x0,0,z0},{x1,0,z0},{x1,0,z1},{x0,0,z1}});
            double left=x==0?0:height(x-1,z,count,shape),right=x==count-1?0:height(x+1,z,count,shape),north=z==0?0:height(x,z-1,count,shape),south=z==count-1?0:height(x,z+1,count,shape);
            if(h>left)face(result,facing,upper,roll,new double[][]{{x0,h,z0},{x0,left,z0},{x0,left,z1},{x0,h,z1}});
            if(h>right)face(result,facing,upper,roll,new double[][]{{x1,h,z0},{x1,h,z1},{x1,right,z1},{x1,right,z0}});
            if(h>north)face(result,facing,upper,roll,new double[][]{{x0,h,z0},{x1,h,z0},{x1,north,z0},{x0,north,z0}});
            if(h>south)face(result,facing,upper,roll,new double[][]{{x0,h,z1},{x0,south,z1},{x1,south,z1},{x1,h,z1}});
        }return result;
    }
    private static void face(List<Face> out,int facing,boolean upper,int roll,double[][] corners){
        Point[] vertices=new Point[4],texture=new Point[4];for(int i=0;i<4;i++){Point local=new Point(corners[i][0],corners[i][1],corners[i][2]);vertices[i]=transform(local,facing,upper,roll);texture[i]=transform(local,facing,upper,0);}
        if(upper){Point swap=vertices[1];vertices[1]=vertices[3];vertices[3]=swap;swap=texture[1];texture[1]=texture[3];texture[3]=swap;}
        int side=direction(vertices[1].subtract(vertices[0]).cross(vertices[2].subtract(vertices[0]))),textureSide=direction(texture[1].subtract(texture[0]).cross(texture[2].subtract(texture[0])));double[] u=new double[4],v=new double[4];
        for(int i=0;i<4;i++){Point p=texture[i];u[i]=textureSide>=4?p.z:p.x;v[i]=textureSide<2?p.z:1-p.y;if(textureSide==5||textureSide==2)u[i]=1-u[i];if(textureSide==0)v[i]=1-v[i];u[i]*=16;v[i]*=16;}
        out.add(new Face(vertices,u,v,side,textureSide));
    }
}
