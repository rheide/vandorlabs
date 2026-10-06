package com.vandorlabs.render;

import java.util.*;

/** Half-plane cuts retain every source attribute, including native and tiled UVs. */
public final class SplitDoorPanel {
    private SplitDoorPanel(){}
    public static double distance(boolean vertical,int panel,double width,double x,double y) {
        double d=(vertical?y-1:x-width/2);
        return panel==0?-d:d;
    }
    public static List<float[]> clip(List<float[]> polygon,boolean vertical,int panel,double width) {
        List<float[]> result=new ArrayList<>();if(polygon.isEmpty())return result;
        float[] previous=polygon.get(polygon.size()-1);
        double before=distance(vertical,panel,width,previous[0],previous[1]);
        for(float[] current:polygon) {
            double after=distance(vertical,panel,width,current[0],current[1]);
            if((before>=0)!=(after>=0)) {
                double t=before/(before-after);float[] cut=new float[current.length];
                for(int i=0;i<cut.length;i++)cut[i]=(float)(previous[i]+t*(current[i]-previous[i]));
                result.add(cut);
            }
            if(after>=0)result.add(current);
            previous=current;before=after;
        }
        return result;
    }
    public static double shiftX(boolean vertical,int panel,double progress) {return vertical?0:(panel==0?-.5:.5)*progress;}
    public static double shiftY(boolean vertical,int panel,double progress) {return vertical?(panel==0?-1:1)*progress:0;}
}
