package com.vandorlabs.canopy;

import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Panel-local UVs; tiled artwork is clipped inside its atlas sprite before drawing. */
public final class CanopyMaterialUv {
    public static boolean fits(CanopyMesh.Face face,int mode,double aspect){
        double[][] uv=coordinates(face,true);double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=-minU,maxV=maxU;
        for(double[] p:uv){minU=Math.min(minU,p[0]);maxU=Math.max(maxU,p[0]);minV=Math.min(minV,p[1]);maxV=Math.max(maxV,p[1]);}
        return com.vandorlabs.render.TextureLayoutMode.fit(mode,maxU-minU,maxV-minV,aspect);
    }
    public static double[][] coordinates(CanopyMesh.Face face,int mode,double aspect){
        boolean fit=fits(face,mode,aspect);double[][] uv=coordinates(face,!fit);
        if(!fit)for(double[] p:uv){p[0]=com.vandorlabs.render.TextureLayoutMode.tileU(p[0],aspect);p[1]=com.vandorlabs.render.TextureLayoutMode.tileV(p[1],aspect);}
        return uv;
    }
    public static double[][] coordinates(CanopyMesh.Face face,boolean tile) {
        Vec3d normal=Vec3d.ZERO;
        for(int i=1;i<face.vertices.length-1;i++){
            normal=face.vertices[i].subtract(face.vertices[0]).crossProduct(face.vertices[i+1].subtract(face.vertices[0]));
            if(normal.lengthSquared()>1e-16)break;
        }
        double length=Math.sqrt(normal.lengthSquared());
        if(length<1e-12)return face.uv;
        normal=normal.scale(1/length);
        Vec3d u=Math.abs(normal.y)>.999?new Vec3d(1,0,0):new Vec3d(0,1,0).crossProduct(normal).normalize();
        if(u.x<-.00001 || Math.abs(u.x)<.00001 && u.z<0)u=u.scale(-1);
        Vec3d v=normal.crossProduct(u).normalize();
        if(v.y<-.00001 || Math.abs(v.y)<.00001 && v.z>0)v=v.scale(-1);
        double[][] uv=new double[face.vertices.length][2];
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(int i=0;i<uv.length;i++){
            uv[i][0]=face.vertices[i].dotProduct(u);uv[i][1]=face.vertices[i].dotProduct(v);
            for(int axis=0;axis<2;axis++){min[axis]=Math.min(min[axis],uv[i][axis]);max[axis]=Math.max(max[axis],uv[i][axis]);}
        }
        if(!tile)for(double[] point:uv)for(int axis=0;axis<2;axis++)point[axis]=(point[axis]-min[axis])/Math.max(1e-12,max[axis]-min[axis]);
        return uv;
    }
    private static final class Vertex {
        final Vec3d pos;final double u,v;
        Vertex(Vec3d pos,double u,double v){this.pos=pos;this.u=u;this.v=v;}
        double at(int axis){return axis==0?u:v;}
        Vertex between(Vertex end,double t){return new Vertex(pos.add(end.pos.subtract(pos).scale(t)),u+(end.u-u)*t,v+(end.v-v)*t);}
    }
    private static List<Vertex> clip(List<Vertex> polygon,int axis,double boundary,boolean above){
        List<Vertex> result=new ArrayList<>();if(polygon.isEmpty())return result;
        Vertex previous=polygon.get(polygon.size()-1);boolean inside=above?previous.at(axis)>=boundary:previous.at(axis)<=boundary;
        for(Vertex current:polygon){
            boolean next=above?current.at(axis)>=boundary:current.at(axis)<=boundary;
            if(next!=inside)result.add(previous.between(current,(boundary-previous.at(axis))/(current.at(axis)-previous.at(axis))));
            if(next)result.add(current);previous=current;inside=next;
        }
        return result;
    }
    /** Rendering only: collision and picking keep the original unsplit panels. */
    public static List<CanopyMesh.Face> drawable(List<CanopyMesh.Face> source){
        boolean tiled=false;for(CanopyMesh.Face face:source)if(face.tiled){tiled=true;break;}if(!tiled)return source;
        List<CanopyMesh.Face> result=new ArrayList<>();
        for(CanopyMesh.Face face:source){
            if(!face.tiled){result.add(face);continue;}
            List<Vertex> polygon=new ArrayList<>();double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=Double.NEGATIVE_INFINITY,maxV=maxU;
            for(int i=0;i<face.vertices.length;i++){double u=face.uv[i][0],v=face.uv[i][1];polygon.add(new Vertex(face.vertices[i],u,v));minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);}
            for(int u=(int)Math.floor(minU);u<Math.ceil(maxU-1e-10);u++)for(int v=(int)Math.floor(minV);v<Math.ceil(maxV-1e-10);v++){
                List<Vertex> part=clip(clip(clip(clip(polygon,0,u,true),0,u+1,false),1,v,true),1,v+1,false);
                if(part.size()<3)continue;
                double area=0;for(int i=1;i<part.size()-1;i++)area+=part.get(i).pos.subtract(part.get(0).pos).crossProduct(part.get(i+1).pos.subtract(part.get(0).pos)).lengthSquared();
                if(area<1e-16)continue;
                CanopyMesh.Face copy=new CanopyMesh.Face();copy.vertices=new Vec3d[part.size()];copy.uv=new double[part.size()][2];
                copy.material=face.material;copy.baseMaterial=face.baseMaterial;copy.glazing=face.glazing;copy.group=face.group;copy.doubleSided=face.doubleSided;
                for(int i=0;i<part.size();i++){Vertex point=part.get(i);copy.vertices[i]=point.pos;copy.uv[i]=new double[]{Math.max(0,Math.min(1,point.u-u)),Math.max(0,Math.min(1,point.v-v))};}
                result.add(copy);
            }
        }
        return result;
    }
    private CanopyMaterialUv(){}
}
