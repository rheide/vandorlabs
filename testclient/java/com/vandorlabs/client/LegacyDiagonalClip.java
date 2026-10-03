package com.vandorlabs.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Clips positions and interpolated vertex attributes without moving the remaining surface. */
final class LegacyDiagonalClip {
    private LegacyDiagonalClip() { }

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
        List<List<double[]>> pieces=new ArrayList<>();
        pieces.add(polygon(bounds,vertices));
        if(bounds!=null)for(int box=6;box<bounds.length;box+=6) {
            List<List<double[]>> next=new ArrayList<>();
            for(List<double[]> piece:pieces) {
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
