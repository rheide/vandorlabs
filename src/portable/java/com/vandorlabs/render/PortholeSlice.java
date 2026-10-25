package com.vandorlabs.render;

import java.util.List;

/** World-independent aperture, frame and pane coordinates for one cell of a porthole group. */
public class PortholeSlice {
    private static final double EPS=1E-7;
    public final List<double[]> polygon,frameQuads,hexEdges,glassQuads;
    public PortholeSlice(List<double[]> polygon,List<double[]> frameQuads,List<double[]> hexEdges,List<double[]> glassQuads){
        this.polygon=polygon;this.frameQuads=frameQuads;this.hexEdges=hexEdges;this.glassQuads=glassQuads;
    }
    /** Interval occupied by glass where a pane crosses a block edge. */
    public double[] edgeOpening(int axis,double boundary){
        double minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;
        for(double[] point:polygon)if(Math.abs(point[axis]-boundary)<EPS){double other=point[1-axis];minimum=Math.min(minimum,other);maximum=Math.max(maximum,other);}
        return maximum-minimum>EPS?new double[]{minimum,maximum}:null;
    }
}
