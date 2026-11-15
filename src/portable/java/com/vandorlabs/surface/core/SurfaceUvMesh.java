package com.vandorlabs.surface.core;

import java.util.*;

/** Atlas-independent, UV-clipped face triangles shared by Source and native terrain. */
public final class SurfaceUvMesh {
    public static final class Vertex {
        public final double x,y,u,v;
        Vertex(double x,double y,double u,double v){this.x=x;this.y=y;this.u=u;this.v=v;}
        double at(int axis){return axis==0?u:v;}
        Vertex between(Vertex other,double t){return new Vertex(x+(other.x-x)*t,y+(other.y-y)*t,u+(other.u-u)*t,v+(other.v-v)*t);}
    }
    public static List<Vertex[]> build(int width,int height,int mode,double aspect,boolean disc){return build(width,height,mode,aspect,disc,-width/2D,-height/2D,width/2D,height/2D);}
    public static List<Vertex[]> buildCell(int width,int height,int mode,double aspect,int u,int v){if(u<0||v<0||u>=width||v>=height)throw new IllegalArgumentException("Surface mesh cell");return build(width,height,mode,aspect,false,u-width/2D,v-height/2D,u+1-width/2D,v+1-height/2D);}
    private static List<Vertex[]> build(int width,int height,int mode,double aspect,boolean disc,double minX,double minY,double maxX,double maxY){
        SurfaceArtwork.checkMode(mode);if(width<1||height<1||width>32||height>32||!Double.isFinite(aspect)||aspect<=0)throw new IllegalArgumentException("Surface mesh bounds");
        double rx=width/2D,ry=height/2D;List<double[][]> polygons=new ArrayList<>();
        if(disc)for(int i=0;i<128;i++){double a=i*Math.PI/64,b=(i+1)*Math.PI/64,rad=width*.43;polygons.add(new double[][]{{0,0},{Math.cos(a)*rad,Math.sin(a)*rad},{Math.cos(b)*rad,Math.sin(b)*rad}});}
        else polygons.add(new double[][]{{minX,minY},{maxX,minY},{maxX,maxY},{minX,maxY}});
        List<Vertex[]> triangles=new ArrayList<>();
        for(double[][] polygon:polygons){List<Vertex> source=new ArrayList<>();
            for(double[] p:polygon){double u,v;if(mode==1){u=(p[0]+rx)/Math.max(1,aspect);v=(p[1]+ry)*Math.min(1,aspect);}else if(mode==2){u=.5+p[0]/(width*Math.min(1,aspect*height/width));v=.5+p[1]/(height*Math.min(1,width/(aspect*height)));}else{u=(p[0]+rx)/width;v=(p[1]+ry)/height;}source.add(new Vertex(p[0],p[1],u,v));}
            double lowU=Double.POSITIVE_INFINITY,lowV=lowU,highU=Double.NEGATIVE_INFINITY,highV=highU;for(Vertex p:source){lowU=Math.min(lowU,p.u);highU=Math.max(highU,p.u);lowV=Math.min(lowV,p.v);highV=Math.max(highV,p.v);}
            for(int u=mode==1?(int)Math.floor(lowU+1e-9):0;u<(mode==1?Math.ceil(highU-1e-9):1);u++)for(int v=mode==1?(int)Math.floor(lowV+1e-9):0;v<(mode==1?Math.ceil(highV-1e-9):1);v++){
                List<Vertex> clipped=clip(clip(clip(clip(source,0,u,true),0,u+1,false),1,v,true),1,v+1,false);if(clipped.size()<3)continue;
                List<Vertex> normalized=new ArrayList<>();for(Vertex p:clipped)normalized.add(new Vertex(p.x,p.y,p.u-u,p.v-v));
                for(int i=1;i+1<normalized.size();i++){Vertex a=normalized.get(0),b=normalized.get(i),c=normalized.get(i+1);if(Math.abs((b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x))>1e-12)triangles.add(new Vertex[]{a,b,c});}
            }
        }
        return Collections.unmodifiableList(triangles);
    }
    private static List<Vertex> clip(List<Vertex> source,int axis,double plane,boolean lower){List<Vertex> result=new ArrayList<>();if(source.isEmpty())return result;Vertex last=source.get(source.size()-1);boolean before=lower?last.at(axis)>=plane:last.at(axis)<=plane;for(Vertex next:source){boolean after=lower?next.at(axis)>=plane:next.at(axis)<=plane;if(before!=after)result.add(last.between(next,(plane-last.at(axis))/(next.at(axis)-last.at(axis))));if(after)result.add(next);last=next;before=after;}return result;}
    /** Clip already normalized triangles to a cell, preserving UVs at shared boundaries. */
    public static List<Vertex[]> cell(List<Vertex[]> triangles,double minX,double minY,double maxX,double maxY){
        List<Vertex[]> result=new ArrayList<Vertex[]>();
        for(Vertex[] triangle:triangles){List<Vertex> polygon=Arrays.asList(triangle);polygon=positionClip(positionClip(positionClip(positionClip(polygon,0,minX,true),0,maxX,false),1,minY,true),1,maxY,false);for(int i=1;i+1<polygon.size();i++){Vertex a=polygon.get(0),b=polygon.get(i),c=polygon.get(i+1);if(Math.abs((b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x))>1e-12)result.add(new Vertex[]{a,b,c});}}
        return Collections.unmodifiableList(result);
    }
    private static List<Vertex> positionClip(List<Vertex> source,int axis,double plane,boolean lower){List<Vertex> result=new ArrayList<Vertex>();if(source.isEmpty())return result;Vertex last=source.get(source.size()-1);double beforeValue=axis==0?last.x:last.y;boolean before=lower?beforeValue>=plane:beforeValue<=plane;for(Vertex next:source){double value=axis==0?next.x:next.y;boolean after=lower?value>=plane:value<=plane;if(before!=after)result.add(last.between(next,(plane-beforeValue)/(value-beforeValue)));if(after)result.add(next);last=next;beforeValue=value;before=after;}return result;}
    private SurfaceUvMesh(){}
}
