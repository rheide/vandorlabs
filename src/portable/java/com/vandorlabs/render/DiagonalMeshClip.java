package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Clips positions and interpolated vertex attributes without moving the remaining surface. */
public final class DiagonalMeshClip {
    private DiagonalMeshClip() { }

    /** Keep n dot position <= distance, interpolating all attached attributes. */
    public static List<double[]> plane(List<double[]> input,double nx,double ny,double nz,double distance) {
        List<double[]> output=new ArrayList<>();
        if(input.isEmpty())return output;
        double[] a=input.get(input.size()-1);double da=nx*a[0]+ny*a[1]+nz*a[2]-distance;
        for(double[] b:input){
            double db=nx*b[0]+ny*b[1]+nz*b[2]-distance;boolean ai=da<=1e-10,bi=db<=1e-10;
            if(ai!=bi){double t=da/(da-db);double[] v=new double[a.length];for(int i=0;i<v.length;i++)v[i]=a[i]+t*(b[i]-a[i]);output.add(v);}
            if(bi)output.add(b);a=b;da=db;
        }
        return output;
    }

    public static List<double[]> polygon(double[] bounds, double[]... vertices) {
        List<double[]> polygon=new ArrayList<>(Arrays.asList(vertices));
        if(bounds==null)return polygon;
        for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++) {
            if(polygon.isEmpty())return polygon;
            double limit=bounds[axis+side*3];
            List<double[]> clipped=new ArrayList<>();
            double[] a=polygon.get(polygon.size()-1);
            for(double[] b:polygon) {
                boolean ai=side==0?a[axis]>=limit:a[axis]<=limit;
                boolean bi=side==0?b[axis]>=limit:b[axis]<=limit;
                if(ai!=bi) {
                    double t=(limit-a[axis])/(b[axis]-a[axis]);
                    double[] v=new double[a.length];
                    for(int i=0;i<v.length;i++)v[i]=a[i]+t*(b[i]-a[i]);
                    clipped.add(v);
                }
                if(bi)clipped.add(b);
                a=b;
            }
            polygon=clipped;
        }
        return polygon;
    }

    /** GL_QUADS representation; triangles use a repeated final vertex. */
    public static List<double[]> quads(double[] bounds,double[]... vertices) {
        return quads(bounds,false,vertices);
    }

    /** Neighbour clipping must retain the thickness face exactly on our cell boundary. */
    public static List<double[]> quadsKeepingContactCaps(double[] bounds,double[]... vertices) {
        return quads(bounds,true,vertices);
    }

    private static List<double[]> quads(double[] bounds,boolean keepCaps,double[]... vertices) {
        if(keepCaps&&bounds!=null&&bounds.length>6)bounds=renderBounds(bounds);
        if(vertices.length==4 && unclipped(bounds,vertices))return Arrays.asList(vertices);
        List<List<double[]>> pieces=new ArrayList<>();
        pieces.add(polygon(bounds,vertices));
        if(bounds!=null)for(int box=6;box<bounds.length;box+=6) {
            List<List<double[]>> next=new ArrayList<>();
            for(List<double[]> piece:pieces) {
                if(keepCaps&&contactCap(bounds,box,piece)){next.add(piece);continue;}
                List<double[]> remainder=piece;
                for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++) {
                    double edge=bounds[box+axis+side*3];
                    List<double[]> outside=half(remainder,axis,edge,side==1,true);
                    if(outside.size()>=3)next.add(outside);
                    remainder=half(remainder,axis,edge,side==0,false);
                }
            }
            pieces=next;
        }
        List<double[]> out=new ArrayList<>();
        for(List<double[]> polygon:pieces) {
            if(polygon.size()==4)out.addAll(polygon);
            else for(int i=1;i+1<polygon.size();i++) {
                out.add(polygon.get(0));out.add(polygon.get(i));
                out.add(polygon.get(i+1));out.add(polygon.get(i+1));
            }
        }
        return out;
    }

    private static boolean contactCap(double[] bounds,int box,List<double[]> piece){
        if(piece.size()<3)return false;
        for(int axis=0;axis<3;axis++){
            int other=(axis+1)%3;double unit=bounds[box+other+3]-bounds[box+other];
            if(unit<=0)continue;
            double lo=bounds[box+axis],hi=bounds[box+axis+3],edge;
            if(Math.abs(lo+unit)<1e-8&&Math.abs(hi)<1e-8)edge=0;
            else if(Math.abs(lo-unit)<1e-8&&Math.abs(hi-2*unit)<1e-8)edge=unit;
            else continue;
            boolean on=true;for(double[] vertex:piece)on&=Math.abs(vertex[axis]-edge)<1e-8;
            if(on)return true;
        }return false;
    }
    private static double[] renderBounds(double[] source){
        double[] bounds=source.clone();
        for(int box=6;box<bounds.length;box+=6)for(int axis=0;axis<3;axis++){
            int other=(axis+1)%3;double unit=bounds[box+other+3]-bounds[box+other];
            if(unit<=0)continue;
            if(Math.abs(bounds[box+axis]+unit)<1e-8&&Math.abs(bounds[box+axis+3]-unit/4096)<1e-8)bounds[box+axis+3]=0;
            else if(Math.abs(bounds[box+axis]-unit*(1-1D/4096))<1e-8&&Math.abs(bounds[box+axis+3]-2*unit)<1e-8)bounds[box+axis]=unit;
        }return bounds;
    }

    private static boolean unclipped(double[] bounds,double[][] vertices) {
        if(bounds==null)return true;
        for(int axis=0;axis<3;axis++)for(double[] v:vertices)
            if(v[axis]<bounds[axis] || v[axis]>bounds[axis+3])return false;
        // Obstacle subtraction also determines tessellation order. Preserve it
        // even when a face lies outside a later axis of an obstacle box.
        if(bounds.length>6)return false;
        return true;
    }

    private static List<double[]> half(List<double[]> input,int axis,double edge,boolean above,boolean strict) {
        List<double[]> out=new ArrayList<>();
        if(input.isEmpty())return out;
        double[] a=input.get(input.size()-1);
        for(double[] b:input) {
            double da=above?a[axis]-edge:edge-a[axis],db=above?b[axis]-edge:edge-b[axis];
            boolean ai=strict?da>0:da>=0,bi=strict?db>0:db>=0;
            if(ai!=bi) {
                double t=(edge-a[axis])/(b[axis]-a[axis]);double[] v=new double[a.length];
                for(int i=0;i<v.length;i++)v[i]=a[i]+t*(b[i]-a[i]);out.add(v);
            }
            if(bi)out.add(b);
            a=b;
        }
        return out;
    }
}
