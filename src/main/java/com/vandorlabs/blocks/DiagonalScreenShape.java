package com.vandorlabs.blocks;

import com.vandorlabs.render.ScreenHousingMesh;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Exact mesh picking and cached one-pixel collision slices of the solid housing. */
public final class DiagonalScreenShape {
    private static final DiagonalScreenShape[][] SHAPES=new DiagonalScreenShape[4][2];
    static {for(int face=0;face<4;face++)for(int upper=0;upper<2;upper++)SHAPES[face][upper]=new DiagonalScreenShape(EnumFacing.getHorizontal(face),upper!=0);}
    public final AxisAlignedBB[] collision;
    public final double[][] outline;
    private final Triangle[] triangles;
    public static DiagonalScreenShape of(EnumFacing facing,boolean upper){return SHAPES[facing.getHorizontalIndex()][upper?1:0];}
    private DiagonalScreenShape(EnumFacing facing,boolean upper){
        collision=new AxisAlignedBB[16];
        for(int z=0;z<16;z++){
            double height=(z+1)/16D;
            collision[z]=PanelPlacement.rotateFromNorth(new AxisAlignedBB(0,upper?1-height:0,z/16D,1,upper?1:height,(z+1)/16D),facing);
        }
        List<Triangle> faces=new ArrayList<>();Map<String,List<Edge>> edges=new LinkedHashMap<>();
        for(ScreenHousingMesh.Face face:ScreenHousingMesh.diagonal(upper).quads){
            Vec3d[] points=new Vec3d[4];for(int i=0;i<4;i++){ScreenHousingMesh.Vertex v=face.vertices[i];points[i]=world(v.x/16,v.y/16,v.z/16,facing);}
            faces.add(new Triangle(points[0],points[1],points[2],facing,upper));faces.add(new Triangle(points[0],points[2],points[3],facing,upper));
            Vec3d normal=points[1].subtract(points[0]).crossProduct(points[2].subtract(points[0])).normalize();
            for(int i=0;i<4;i++){Vec3d a=points[i],b=points[(i+1)%4];String ka=key(a),kb=key(b),key=ka.compareTo(kb)<0?ka+":"+kb:kb+":"+ka;edges.computeIfAbsent(key,k->new ArrayList<>()).add(new Edge(a,b,normal));}
        }
        triangles=faces.toArray(new Triangle[0]);List<double[]> lines=new ArrayList<>();
        for(List<Edge> shared:edges.values())if(shared.size()!=2 || Math.abs(shared.get(0).normal.dotProduct(shared.get(1).normal))<.99999){Edge e=shared.get(0);lines.add(new double[]{e.a.x,e.a.y,e.a.z,e.b.x,e.b.y,e.b.z});}
        outline=lines.toArray(new double[0][]);
    }
    private static String key(Vec3d v){return v.x+","+v.y+","+v.z;}
    private static final class Edge {final Vec3d a,b,normal;Edge(Vec3d a,Vec3d b,Vec3d normal){this.a=a;this.b=b;this.normal=normal;}}
    public static Vec3d world(double x,double y,double z,EnumFacing face){
        switch(face){case EAST:return new Vec3d(1-z,y,x);case SOUTH:return new Vec3d(1-x,y,1-z);case WEST:return new Vec3d(z,y,1-x);default:return new Vec3d(x,y,z);}
    }
    public RayTraceResult trace(BlockPos pos,Vec3d start,Vec3d end){
        Vec3d a=start.subtract(new Vec3d(pos)),delta=end.subtract(start);double closest=Double.POSITIVE_INFINITY;Triangle selected=null;
        for(Triangle triangle:triangles){double t=triangle.hit(a,delta);if(t<closest){closest=t;selected=triangle;}}
        if(selected==null)return null;
        EnumFacing side=EnumFacing.getFacingFromVector((float)selected.normal.x,(float)selected.normal.y,(float)selected.normal.z);
        return new RayTraceResult(start.add(delta.scale(closest)),side,pos);
    }
    private static final class Triangle {
        final Vec3d a,e1,e2,normal;
        Triangle(Vec3d a,Vec3d b,Vec3d c,EnumFacing facing,boolean upper){
            this.a=a;e1=b.subtract(a);e2=c.subtract(a);Vec3d n=e1.crossProduct(e2).normalize();
            Vec3d center=a.add(b).add(c).scale(1D/3);
            // The housing has small concave end caps and mixed legacy windings.
            // Probe the actual profile once when baking to orient every face,
            // including rays that leave the housing from inside it.
            Vec3d probe=RedstoneScreenInteractions.local(center.add(n.scale(.00001)),facing).scale(1D/16);
            double height=probe.z<1D/16?1D/16:probe.z>=15D/16?1:probe.z;
            boolean inside=probe.x>=0 && probe.x<=1 && probe.y>=0 && probe.y<=1 && probe.z>=0 && probe.z<=1 && (upper?probe.y>=1-height:probe.y<=height);
            normal=inside?n.scale(-1):n;
        }
        double hit(Vec3d start,Vec3d d){
            double px=d.y*e2.z-d.z*e2.y,py=d.z*e2.x-d.x*e2.z,pz=d.x*e2.y-d.y*e2.x;
            double det=e1.x*px+e1.y*py+e1.z*pz;if(Math.abs(det)<1e-10)return Double.POSITIVE_INFINITY;
            double tx=start.x-a.x,ty=start.y-a.y,tz=start.z-a.z;
            double u=(tx*px+ty*py+tz*pz)/det;if(u< -1e-8 || u>1+1e-8)return Double.POSITIVE_INFINITY;
            double qx=ty*e1.z-tz*e1.y,qy=tz*e1.x-tx*e1.z,qz=tx*e1.y-ty*e1.x;
            double v=(d.x*qx+d.y*qy+d.z*qz)/det;if(v< -1e-8 || u+v>1+1e-8)return Double.POSITIVE_INFINITY;
            double t=(e2.x*qx+e2.y*qy+e2.z*qz)/det;return t>=0 && t<=1?t:Double.POSITIVE_INFINITY;
        }
    }
}
