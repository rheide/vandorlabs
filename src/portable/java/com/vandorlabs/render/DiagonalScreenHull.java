package com.vandorlabs.render;

import java.util.*;

/** Exact mesh picking and cached one-pixel collision slices of the solid housing. */
public final class DiagonalScreenHull {
    private static final DiagonalScreenHull[][] SHAPES=new DiagonalScreenHull[6][2];
    static {for(int face=2;face<6;face++)for(int upper=0;upper<2;upper++)SHAPES[face][upper]=new DiagonalScreenHull(face,upper!=0);}
    public final Bounds[] collision;
    public final double[][] outline;
    private final Triangle[] triangles;
    public static DiagonalScreenHull of(int facing,boolean upper){return SHAPES[facing][upper?1:0];}
    private DiagonalScreenHull(int facing,boolean upper){
        collision=new Bounds[16];
        for(int z=0;z<16;z++){
            double height=(z+1)/16D;
            collision[z]=rotate(new Bounds(0,upper?1-height:0,z/16D,1,upper?1:height,(z+1)/16D),facing);
        }
        List<Triangle> faces=new ArrayList<>();Map<String,List<Edge>> edges=new LinkedHashMap<>();
        for(ScreenHousingMesh.Face face:ScreenHousingMesh.diagonal(upper).quads){
            Vec[] points=new Vec[4];for(int i=0;i<4;i++){ScreenHousingMesh.Vertex v=face.vertices[i];points[i]=world(v.x/16,v.y/16,v.z/16,facing);}
            faces.add(new Triangle(points[0],points[1],points[2],facing,upper));faces.add(new Triangle(points[0],points[2],points[3],facing,upper));
            Vec normal=points[1].subtract(points[0]).crossProduct(points[2].subtract(points[0])).normalize();
            for(int i=0;i<4;i++){Vec a=points[i],b=points[(i+1)%4];String ka=key(a),kb=key(b),key=ka.compareTo(kb)<0?ka+":"+kb:kb+":"+ka;edges.computeIfAbsent(key,k->new ArrayList<>()).add(new Edge(a,b,normal));}
        }
        triangles=faces.toArray(new Triangle[0]);List<double[]> lines=new ArrayList<>();
        for(List<Edge> shared:edges.values())if(shared.size()!=2 || Math.abs(shared.get(0).normal.dotProduct(shared.get(1).normal))<.99999){Edge e=shared.get(0);lines.add(new double[]{e.a.x,e.a.y,e.a.z,e.b.x,e.b.y,e.b.z});}
        outline=lines.toArray(new double[0][]);
    }
    private static String key(Vec v){return v.x+","+v.y+","+v.z;}
    private static final class Edge {final Vec a,b,normal;Edge(Vec a,Vec b,Vec normal){this.a=a;this.b=b;this.normal=normal;}}
    private static Vec world(double x,double y,double z,int face){
        switch(face){case 5:return new Vec(1-z,y,x);case 3:return new Vec(1-x,y,1-z);case 4:return new Vec(z,y,1-x);default:return new Vec(x,y,z);}
    }
    public static double[] worldPoint(double x,double y,double z,int facing){Vec p=world(x,y,z,facing);return new double[]{p.x,p.y,p.z};}
    public static final class Hit {
        public final double x,y,z,nx,ny,nz;
        private Hit(Vec point,Vec normal){x=point.x;y=point.y;z=point.z;nx=normal.x;ny=normal.y;nz=normal.z;}
    }
    public Hit trace(double sx,double sy,double sz,double ex,double ey,double ez){
        Vec a=new Vec(sx,sy,sz),delta=new Vec(ex-sx,ey-sy,ez-sz);double closest=Double.POSITIVE_INFINITY;Triangle selected=null;
        for(Triangle triangle:triangles){double t=triangle.hit(a,delta);if(t<closest){closest=t;selected=triangle;}}
        return selected==null?null:new Hit(a.add(delta.scale(closest)),selected.normal);
    }
    private static final class Triangle {
        final Vec a,e1,e2,normal;
        Triangle(Vec a,Vec b,Vec c,int facing,boolean upper){
            this.a=a;e1=b.subtract(a);e2=c.subtract(a);Vec n=e1.crossProduct(e2).normalize();
            Vec center=a.add(b).add(c).scale(1D/3);
            // The housing has small concave end caps and mixed legacy windings.
            // Probe the actual profile once when baking to orient every face,
            // including rays that leave the housing from inside it.
            double[] local=com.vandorlabs.ui.RedstoneScreenLayout.local(center.x+n.x*.00001,center.y+n.y*.00001,center.z+n.z*.00001,facing);Vec probe=new Vec(local[0]/16,local[1]/16,local[2]/16);
            double height=probe.z<1D/16?1D/16:probe.z>=15D/16?1:probe.z;
            boolean inside=probe.x>=0 && probe.x<=1 && probe.y>=0 && probe.y<=1 && probe.z>=0 && probe.z<=1 && (upper?probe.y>=1-height:probe.y<=height);
            normal=inside?n.scale(-1):n;
        }
        double hit(Vec start,Vec d){
            double px=d.y*e2.z-d.z*e2.y,py=d.z*e2.x-d.x*e2.z,pz=d.x*e2.y-d.y*e2.x;
            double det=e1.x*px+e1.y*py+e1.z*pz;if(Math.abs(det)<1e-10)return Double.POSITIVE_INFINITY;
            double tx=start.x-a.x,ty=start.y-a.y,tz=start.z-a.z;
            double u=(tx*px+ty*py+tz*pz)/det;if(u< -1e-8 || u>1+1e-8)return Double.POSITIVE_INFINITY;
            double qx=ty*e1.z-tz*e1.y,qy=tz*e1.x-tx*e1.z,qz=tx*e1.y-ty*e1.x;
            double v=(d.x*qx+d.y*qy+d.z*qz)/det;if(v< -1e-8 || u+v>1+1e-8)return Double.POSITIVE_INFINITY;
            double t=(e2.x*qx+e2.y*qy+e2.z*qz)/det;return t>=0 && t<=1?t:Double.POSITIVE_INFINITY;
        }
    }
    public static final class Bounds {
        public final double minX,minY,minZ,maxX,maxY,maxZ;
        public Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){this.minX=minX;this.minY=minY;this.minZ=minZ;this.maxX=maxX;this.maxY=maxY;this.maxZ=maxZ;}
    }
    private static Bounds rotate(Bounds b,int facing){
        if(facing==5)return new Bounds(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);
        if(facing==3)return new Bounds(1-b.maxX,b.minY,1-b.maxZ,1-b.minX,b.maxY,1-b.minZ);
        if(facing==4)return new Bounds(b.minZ,b.minY,1-b.maxX,b.maxZ,b.maxY,1-b.minX);
        return b;
    }
    private static final class Vec {
        final double x,y,z;
        Vec(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        Vec add(Vec b){return new Vec(x+b.x,y+b.y,z+b.z);}Vec subtract(Vec b){return new Vec(x-b.x,y-b.y,z-b.z);}Vec scale(double value){return new Vec(x*value,y*value,z*value);}
        double dotProduct(Vec b){return x*b.x+y*b.y+z*b.z;}
        Vec crossProduct(Vec b){return new Vec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
        Vec normalize(){double length=Math.sqrt(x*x+y*y+z*z);return length<1e-4?new Vec(0,0,0):scale(1/length);}
    }

}
