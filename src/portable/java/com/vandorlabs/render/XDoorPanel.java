package com.vandorlabs.render;

import java.util.*;

/** Four diagonal half-plane intersections in the unscaled, two-leaf door coordinates. */
public final class XDoorPanel {
    private XDoorPanel(){}
    // Left, right, top, bottom. Keep X/Y travel paired with these clipping regions.
    private static final double[][][] PLANES={{{-1,1,0},{-1,-1,2}},{{1,-1,0},{1,1,-2}},{{-1,1,0},{1,1,-2}},{{1,-1,0},{-1,-1,2}}};
    public static double shiftX(int panel,double progress){return panel==0?-progress:panel==1?progress:0;}
    public static double shiftY(int panel,double progress){return panel==2?progress:panel==3?-progress:0;}
    public static double distance(int panel,int plane,double x,double y){double[] p=PLANES[panel][plane];return p[0]*x+p[1]*y+p[2];}
    /** Sutherland-Hodgman interpolation preserves UVs, depth and normals at each cut. */
    public static List<float[]> clip(List<float[]> polygon,int hand,int panel) {
        List<float[]> result=polygon;
        for(int plane=0;plane<2;plane++) {
            List<float[]> next=new ArrayList<>();
            if(result.isEmpty())return result;
            float[] previous=result.get(result.size()-1);
            double before=distance(panel,plane,previous[0]+hand,previous[1]);
            for(float[] current:result) {
                double after=distance(panel,plane,current[0]+hand,current[1]);
                if((before>=0)!=(after>=0)) {
                    double t=before/(before-after);float[] cut=new float[current.length];
                    for(int i=0;i<cut.length;i++)cut[i]=(float)(previous[i]+t*(current[i]-previous[i]));
                    next.add(cut);
                }
                if(after>=0)next.add(current);
                previous=current;before=after;
            }
            result=next;
        }
        return result;
    }
}
