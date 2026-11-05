package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

/** Current panel group UV arithmetic and repeat-boundary clipping; atlas reads remain adapter-owned. */
public final class PanelTextureMapping {
    public static double stretch(double value,int offset,double border,double span){
        double mapped=(offset+value-border)/span;return Math.max(0,Math.min(1,mapped));
    }
    public static double u(boolean fit,double value,int column,int columns,double border,double unit,double aspect){
        return fit?stretch(value,column,border,columns-2*border):TextureLayoutMode.tileU((column+value)*unit,aspect);
    }
    public static double v(boolean fit,double value,int row,int rows,double border,double unit,double aspect){
        return fit?stretch(value,rows-1-row,border,rows-2*border):TextureLayoutMode.tileV((rows-1-row+value)*unit,aspect);
    }
    public static List<double[]> clip(List<double[]> polygon,int axis,double edge,boolean greater){
        List<double[]> out=new ArrayList<>();
        for(int j=0;j<polygon.size();j++) {
            double[] before=polygon.get((j+polygon.size()-1)%polygon.size()),after=polygon.get(j);
            boolean a=greater?before[axis]>=edge:before[axis]<=edge,b=greater?after[axis]>=edge:after[axis]<=edge;
            if(a!=b){double t=(edge-before[axis])/(after[axis]-before[axis]);double[] point=new double[before.length];for(int k=0;k<point.length;k++)point[k]=before[k]+t*(after[k]-before[k]);point[axis]=edge;out.add(point);}
            if(b)out.add(after);
        }
        return out;
    }
    private PanelTextureMapping(){}
}
