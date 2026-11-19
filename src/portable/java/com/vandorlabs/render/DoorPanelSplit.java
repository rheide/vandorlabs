package com.vandorlabs.render;

import com.vandorlabs.persistence.SpaceDoorData;
import java.util.ArrayList;
import java.util.List;

/** Clip Large Door artwork in unscaled, two-leaf coordinates and move the pieces apart. */
public enum DoorPanelSplit {
    DIAGONAL(new double[][][]{{{-1,1,0},{-1,-1,2}},{{1,-1,0},{1,1,-2}},{{-1,1,0},{1,1,-2}},{{1,-1,0},{-1,-1,2}}}),
    VERTICAL(new double[][][]{{{0,-1,1}},{{0,1,-1}}});

    private final double[][][] planes;

    DoorPanelSplit(double[][][] planes) { this.planes=planes; }

    public static DoorPanelSplit forDirection(int direction) {
        return direction==SpaceDoorData.VERTICAL_SPLIT?VERTICAL:direction==SpaceDoorData.X_SPLIT?DIAGONAL:null;
    }

    public int panelCount() { return planes.length; }
    public int planeCount(int panel) { return planes[panel].length; }

    /** A bare Large Door retains one world-space pixel at the edges. */
    public static double travel(double progress,boolean framed,double scale) {
        return progress*(framed?1:1-1/(16*scale));
    }

    public double shiftX(int panel,double travel) {
        return this==DIAGONAL?(panel==0?-travel:panel==1?travel:0):0;
    }

    public double shiftY(int panel,double travel) {
        if(this==VERTICAL)return panel==0?-travel:travel;
        return panel==2?travel:panel==3?-travel:0;
    }

    public double distance(int panel,int plane,double x,double y) {
        double[] p=planes[panel][plane];
        return p[0]*x+p[1]*y+p[2];
    }

    /** Interpolate atlas coordinates, depth and normals along the cut. */
    public List<float[]> clip(List<float[]> polygon,int hand,int panel) {
        List<float[]> result=polygon;
        for(int plane=0;plane<planeCount(panel);plane++) {
            if(result.isEmpty())return result;
            List<float[]> next=new ArrayList<>();
            float[] previous=result.get(result.size()-1);
            double before=distance(panel,plane,previous[0]+hand,previous[1]);
            for(float[] current:result) {
                double after=distance(panel,plane,current[0]+hand,current[1]);
                if((before>=0)!=(after>=0)) {
                    double t=before/(before-after);
                    float[] cut=new float[current.length];
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

    /** Close a cut in the two-pixel sliding slab using the source face's atlas coordinates. */
    public float[][] cap(int hand,int panel,int plane,float[] a,float[] b) {
        if(Math.abs(distance(panel,plane,a[0]+hand,a[1]))>1e-6
                ||Math.abs(distance(panel,plane,b[0]+hand,b[1]))>1e-6)return null;
        double length=Math.hypot(a[0]-b[0],a[1]-b[1]);
        if(length<1e-6)return null;
        float[] c=b.clone(),d=a.clone();
        c[2]=d[2]=7F/16;
        float nx=(float)((b[1]-a[1])/length),ny=(float)((a[0]-b[0])/length);
        float[][] points={a.clone(),d,c,b.clone()};
        for(float[] p:points){p[5]=nx;p[6]=ny;p[7]=0;}
        return points;
    }
}
