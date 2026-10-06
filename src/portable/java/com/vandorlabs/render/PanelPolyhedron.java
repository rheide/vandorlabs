package com.vandorlabs.render;

import java.util.*;

/** Convex cuts seal their new faces; rendering and collision share the same half-spaces. */
public final class PanelPolyhedron {
    private PanelPolyhedron(){}
    public static List<double[][]> faces(double[][] corners,PanelMotion motion,int panel,double pose) {
        List<double[][]> faces=new ArrayList<>();
        for(int[] indices:TrapdoorGeometry.FACES){double[][] face=new double[4][];for(int i=0;i<4;i++)face[i]=corners[indices[i]].clone();faces.add(face);}
        for(int i=0;i<motion.planes();i++)faces=clip(faces,motion.plane(panel,i));
        double[] shift=motion.shift(panel,pose);
        for(double[][] face:faces)for(double[] point:face)for(int axis=0;axis<3;axis++)point[axis]+=shift[axis];
        return faces;
    }
    public static List<double[][]> clip(List<double[][]> faces,double[] plane) {
        List<double[][]> result=new ArrayList<>();List<double[]> cuts=new ArrayList<>();
        for(double[][] face:faces) {
            List<double[]> polygon=new ArrayList<>();double[] previous=face[face.length-1];double before=PanelMotion.distance(plane,previous);
            for(double[] current:face) {
                double after=PanelMotion.distance(plane,current);
                if((before>=0)!=(after>=0)) {
                    double t=before/(before-after);double[] cut=new double[3];for(int i=0;i<3;i++)cut[i]=previous[i]+t*(current[i]-previous[i]);
                    polygon.add(cut);boolean duplicate=false;for(double[] other:cuts)if(distanceSquared(other,cut)<1e-16){duplicate=true;break;}if(!duplicate)cuts.add(cut.clone());
                }
                if(after>=0)polygon.add(current.clone());previous=current;before=after;
            }
            if(polygon.size()>=3)result.add(polygon.toArray(new double[0][]));
        }
        if(cuts.size()>=3) {
            double[] center=new double[3];for(double[] point:cuts)for(int i=0;i<3;i++)center[i]+=point[i]/cuts.size();
            double[] n=unit(new double[]{-plane[0],-plane[1],-plane[2]});
            double[] a=unit(cross(Math.abs(n[0])<.8?new double[]{1,0,0}:new double[]{0,1,0},n)),b=cross(n,a);
            cuts.sort(Comparator.comparingDouble(point->Math.atan2(dotDelta(point,center,b),dotDelta(point,center,a))));
            result.add(cuts.toArray(new double[0][]));
        }
        return result;
    }
    public static double[] bounds(List<double[][]> faces) {
        double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(double[][] face:faces)for(double[] point:face)for(int i=0;i<3;i++){bounds[i]=Math.min(bounds[i],point[i]);bounds[i+3]=Math.max(bounds[i+3],point[i]);}return bounds;
    }
    public static double[] unit(double[] value){double length=Math.sqrt(dot(value,value));for(int i=0;i<3;i++)value[i]/=length;return value;}
    public static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double dotDelta(double[] point,double[] center,double[] axis){return (point[0]-center[0])*axis[0]+(point[1]-center[1])*axis[1]+(point[2]-center[2])*axis[2];}
    public static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static double distanceSquared(double[] a,double[] b){double result=0;for(int i=0;i<3;i++)result+=(a[i]-b[i])*(a[i]-b[i]);return result;}
}
