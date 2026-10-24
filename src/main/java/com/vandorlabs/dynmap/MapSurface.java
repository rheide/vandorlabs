package com.vandorlabs.dynmap;

import net.minecraft.util.math.Vec3d;
import org.dynmap.renderer.*;
import java.util.*;

/** Convex faces retain authored artwork through cell clipping, slopes and rigid transforms. */
final class MapSurface {
    static final class Vertex {
        final Vec3d p;final double u,v;
        Vertex(Vec3d p,double u,double v){this.p=p;this.u=u;this.v=v;}
        Vertex between(Vertex other,double t){return new Vertex(p.add(other.p.subtract(p).scale(t)),u+(other.u-u)*t,v+(other.v-v)*t);}
        double at(int axis){return axis==0?p.x:axis==1?p.y:axis==2?p.z:axis==3?u:v;}
    }
    static void emit(MapDataContext context,List<RenderPatch> out,List<Vertex> polygon,int texture){
        for(int axis=0;axis<3;axis++){polygon=clip(polygon,axis,0,true);polygon=clip(polygon,axis,1,false);}
        if(polygon.size()<3)return;
        double minU=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,minV=minU,maxV=maxU;
        for(Vertex vertex:polygon){minU=Math.min(minU,vertex.u);maxU=Math.max(maxU,vertex.u);minV=Math.min(minV,vertex.v);maxV=Math.max(maxV,vertex.v);}
        // Dynmap patch bounds address one texture tile. Split repeated authored
        // UVs rather than stretching the material across a large canopy face.
        for(int u=(int)Math.floor(minU+1e-9);u<Math.ceil(maxU-1e-9);u++)for(int v=(int)Math.floor(minV+1e-9);v<Math.ceil(maxV-1e-9);v++){
            List<Vertex> tile=clip(clip(clip(clip(polygon,3,u,true),3,u+1,false),4,v,true),4,v+1,false);
            List<Vertex> normalized=new ArrayList<>();for(Vertex vertex:tile)normalized.add(new Vertex(vertex.p,vertex.u-u,vertex.v-v));
            patch(context,out,normalized,texture);
        }
    }
    private static void patch(MapDataContext context,List<RenderPatch> out,List<Vertex> polygon,int texture){
        if(polygon.size()<3)return;
        Vertex p=polygon.get(0),q=null,r=null;double det=0;
        for(int i=1;i<polygon.size()&&Math.abs(det)<1e-12;i++)for(int j=i+1;j<polygon.size();j++){
            q=polygon.get(i);r=polygon.get(j);det=(q.u-p.u)*(r.v-p.v)-(r.u-p.u)*(q.v-p.v);if(Math.abs(det)>=1e-12)break;
        }
        if(Math.abs(det)<1e-12)return;
        double du=q.u-p.u,dv=q.v-p.v,eu=r.u-p.u,ev=r.v-p.v;
        Vec3d edge=q.p.subtract(p.p),other=r.p.subtract(p.p);
        Vec3d U=edge.scale(ev).subtract(other.scale(dv)).scale(1/det),V=other.scale(du).subtract(edge.scale(eu)).scale(1/det),origin=p.p.subtract(U.scale(p.u)).subtract(V.scale(p.v)),endU=origin.add(U),endV=origin.add(V);
        SortedSet<Double> cuts=new TreeSet<>();for(Vertex vertex:polygon)cuts.add(vertex.u);
        Double previous=null;
        for(double high:cuts){
            if(previous==null){previous=high;continue;}double low=previous;previous=high;if(high-low<1e-10)continue;
            double middle=(low+high)/2,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;double[] lower=null,upper=null;
            for(int i=0;i<polygon.size();i++){
                Vertex a=polygon.get(i),b=polygon.get((i+1)%polygon.size());
                if(middle<=Math.min(a.u,b.u)||middle>=Math.max(a.u,b.u))continue;
                double slope=(b.v-a.v)/(b.u-a.u),at=a.v+slope*(middle-a.u);
                double[] bounds={a.v+slope*(low-a.u),a.v+slope*(high-a.u)};
                if(at<min){min=at;lower=bounds;}if(at>max){max=at;upper=bounds;}
            }
            if(lower==null||upper==null||max-min<1e-10)continue;
            RenderPatch patch=context.getPatchFactory().getPatch(origin.x,origin.y,origin.z,endU.x,endU.y,endU.z,endV.x,endV.y,endV.z,low,high,lower[0],lower[1],upper[0],upper[1],RenderPatchFactory.SideVisible.BOTH,texture);
            if(patch!=null)out.add(patch);
        }
    }
    private static List<Vertex> clip(List<Vertex> in,int axis,double plane,boolean lower){
        List<Vertex> out=new ArrayList<>();if(in.isEmpty())return out;Vertex previous=in.get(in.size()-1);boolean inside=lower?previous.at(axis)>=plane:previous.at(axis)<=plane;
        for(Vertex next:in){boolean nextInside=lower?next.at(axis)>=plane:next.at(axis)<=plane;if(inside!=nextInside)out.add(previous.between(next,(plane-previous.at(axis))/(next.at(axis)-previous.at(axis))));if(nextInside)out.add(next);previous=next;inside=nextInside;}
        List<Vertex> unique=new ArrayList<>();
        for(Vertex vertex:out)if(unique.isEmpty()||unique.get(unique.size()-1).p.squareDistanceTo(vertex.p)>1e-18)unique.add(vertex);
        if(unique.size()>1&&unique.get(0).p.squareDistanceTo(unique.get(unique.size()-1).p)<1e-18)unique.remove(unique.size()-1);
        return unique;
    }
}
