package com.vandorlabs.canopy;

import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Remove coincident drawing surfaces after posing, preserving the complete moving mechanism. */
public final class CanopySurfaces {
    private static final double EPS=1e-9;
    private static final class Surface {
        final CanopyMesh.Face face;final Vec3d normal;final int u,v;final double[] bounds=new double[4];
        Surface(CanopyMesh.Face face,Vec3d normal){
            this.face=face;this.normal=normal;int drop=Math.abs(normal.x)>Math.abs(normal.y)?0:1;
            if(Math.abs(normal.z)>Math.abs(at(normal,drop)))drop=2;
            u=drop==0?1:0;v=drop==2?1:2;
            bounds[0]=bounds[2]=Double.POSITIVE_INFINITY;bounds[1]=bounds[3]=Double.NEGATIVE_INFINITY;
            for(Vec3d p:face.vertices){bounds[0]=Math.min(bounds[0],at(p,u));bounds[1]=Math.max(bounds[1],at(p,u));bounds[2]=Math.min(bounds[2],at(p,v));bounds[3]=Math.max(bounds[3],at(p,v));}
        }
        boolean overlaps(Surface other){return bounds[0]<other.bounds[1]-EPS && bounds[1]>other.bounds[0]+EPS && bounds[2]<other.bounds[3]-EPS && bounds[3]>other.bounds[2]+EPS;}
    }
    private static double at(Vec3d p,int axis){return axis==0?p.x:axis==1?p.y:p.z;}
    private static Vec3d normal(CanopyMesh.Face face){
        for(int i=1;i<face.vertices.length-1;i++){Vec3d n=face.vertices[i].subtract(face.vertices[0]).crossProduct(face.vertices[i+1].subtract(face.vertices[0]));double length=Math.sqrt(n.lengthSquared());if(length>1e-12)return n.scale(1/length);}return Vec3d.ZERO;
    }
    private static long rounded(double value){return Math.round(value*1e7);}
    public static List<CanopyMesh.Face> visible(List<CanopyMesh.Face> source){
        Map<String,List<Surface>> planes=new LinkedHashMap<>();
        for(CanopyMesh.Face face:source){
            Vec3d n=normal(face);if(n.lengthSquared()==0)continue;
            Surface surface=new Surface(face,n);
            if((Math.abs(n.x)>EPS?n.x:Math.abs(n.y)>EPS?n.y:n.z)<0)n=n.scale(-1);
            String key=rounded(n.x)+":"+rounded(n.y)+":"+rounded(n.z)+":"+rounded(n.dotProduct(face.vertices[0]));
            planes.computeIfAbsent(key,k->new ArrayList<>()).add(surface);
        }
        List<CanopyMesh.Face> result=new ArrayList<>(source.size());
        for(List<Surface> plane:planes.values())for(int index=0;index<plane.size();index++){
            Surface surface=plane.get(index);CanopyMesh.Face face=surface.face;
            List<double[]> polygon=new ArrayList<>();
            for(int i=0;i<face.vertices.length;i++){Vec3d p=face.vertices[i];polygon.add(new double[]{p.x,p.y,p.z,face.uv[i][0],face.uv[i][1]});}
            List<List<double[]>> pieces=new ArrayList<>();pieces.add(polygon);
            for(int j=0;j<plane.size() && !pieces.isEmpty();j++){
                Surface other=plane.get(j);boolean glass=face.baseMaterial.equals("glass"),otherGlass=other.face.baseMaterial.equals("glass");
                if(j==index || j<index && !(glass && !otherGlass) || !glass && otherGlass || !surface.overlaps(other))continue;
                if(!face.doubleSided && !other.face.doubleSided && surface.normal.dotProduct(other.normal)<=0)continue;
                List<double[]> cut=new ArrayList<>();for(Vec3d p:other.face.vertices)cut.add(new double[]{p.x,p.y,p.z});
                List<List<double[]>> next=new ArrayList<>();for(List<double[]> piece:pieces)next.addAll(subtract(piece,cut,surface.u,surface.v));pieces=next;
            }
            if(pieces.size()==1 && pieces.get(0)==polygon){result.add(face);continue;}
            for(List<double[]> piece:pieces){
                CanopyMesh.Face copy=new CanopyMesh.Face();copy.vertices=new Vec3d[piece.size()];copy.uv=new double[piece.size()][2];
                copy.material=face.material;copy.baseMaterial=face.baseMaterial;copy.group=face.group;copy.doubleSided=face.doubleSided;copy.glazing=face.glazing;copy.tiled=face.tiled;
                for(int i=0;i<piece.size();i++){double[] p=piece.get(i);copy.vertices[i]=new Vec3d(p[0],p[1],p[2]);copy.uv[i]=new double[]{p[3],p[4]};}result.add(copy);
            }
        }
        return result;
    }
    private static double area(List<double[]> polygon,int u,int v){double sum=0;for(int i=0;i<polygon.size();i++){double[] a=polygon.get(i),b=polygon.get((i+1)%polygon.size());sum+=a[u]*b[v]-b[u]*a[v];}return sum/2;}
    private static double distance(double[] p,double[] a,double[] b,int u,int v,double sign){return sign*((b[u]-a[u])*(p[v]-a[v])-(b[v]-a[v])*(p[u]-a[u]));}
    private static List<List<double[]>> subtract(List<double[]> polygon,List<double[]> cut,int u,int v){
        List<double[]> remaining=polygon;List<List<double[]>> pieces=new ArrayList<>();double sign=area(cut,u,v)>0?1:-1;
        for(int edge=0;edge<cut.size();edge++){
            double[] a=cut.get(edge),b=cut.get((edge+1)%cut.size());List<double[]> inside=new ArrayList<>(),outside=new ArrayList<>();
            for(int i=0;i<remaining.size();i++){
                double[] p=remaining.get(i),q=remaining.get((i+1)%remaining.size());double d=distance(p,a,b,u,v,sign),e=distance(q,a,b,u,v,sign);
                (d>=-EPS?inside:outside).add(p);
                if(d>EPS && e<-EPS || d<-EPS && e>EPS){double t=d/(d-e);double[] intersection=new double[p.length];for(int axis=0;axis<p.length;axis++)intersection[axis]=p[axis]+t*(q[axis]-p[axis]);inside.add(intersection);outside.add(intersection);}
            }
            if(outside.size()>=3 && Math.abs(area(outside,u,v))>EPS)pieces.add(outside);
            remaining=inside;
            if(remaining.size()<3 || Math.abs(area(remaining,u,v))<=EPS)return Collections.singletonList(polygon);
        }
        return pieces;
    }
    private CanopySurfaces(){}
}
