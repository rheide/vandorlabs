package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

/** Inclusive polygon clipping; intersections retain every supplied vertex attribute. */
public final class PolygonClip {
    private PolygonClip(){}
    public static List<double[]> clip(List<double[]> polygon,int axis,double edge,boolean greater){
        List<double[]> out=new ArrayList<>();
        for(int j=0;j<polygon.size();j++){
            double[] before=polygon.get((j+polygon.size()-1)%polygon.size()),after=polygon.get(j);
            boolean a=greater?before[axis]>=edge:before[axis]<=edge,b=greater?after[axis]>=edge:after[axis]<=edge;
            if(a!=b){double t=(edge-before[axis])/(after[axis]-before[axis]);double[] point=new double[before.length];for(int k=0;k<point.length;k++)point[k]=before[k]+t*(after[k]-before[k]);point[axis]=edge;out.add(point);}
            if(b)out.add(after);
        }
        return out;
    }
}
