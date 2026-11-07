package com.vandorlabs.segmented;

import com.vandorlabs.render.PanelTextureMapping;
import java.util.*;

/** Extends a prepared axis-aligned upright face without stretching its authored texture density. */
public final class SegmentedGateConnectorUV {
    public interface Sink {void vertex(double x,double y,double z,double u,double v,float nx,float ny,float nz);}
    private SegmentedGateConnectorUV(){}
    /** Source quad: four XYZ/normalized-UV/normal records (eight floats each), before atlas mapping. */
    public static int emit(float[] source,SegmentedGateShape.Box target,Sink sink){
        if(source==null||source.length!=32||target==null||sink==null)throw new IllegalArgumentException("Gate connector face");
        for(float value:source)if(Float.isNaN(value)||Float.isInfinite(value))throw new IllegalArgumentException("Nonfinite gate face");
        if(target.x0<0||target.x1>1||target.y0<0||target.y1>1||target.z0<7D/16||target.z1>9D/16)throw new IllegalArgumentException("Connector outside cell envelope");
        for(int vertex=0;vertex<4;vertex++){for(int a=0;a<3;a++)if(source[vertex*8+a]<-1||source[vertex*8+a]>2)throw new IllegalArgumentException("Gate face extent");for(int a=3;a<5;a++)if(source[vertex*8+a]<-1e-5||source[vertex*8+a]>1.00001)throw new IllegalArgumentException("Gate face UV range");}
        int normalAxis=-1;for(int a=0;a<3;a++)if(Math.abs(source[5+a])>1e-6){if(normalAxis>=0||Math.abs(Math.abs(source[5+a])-1)>1e-6)throw new IllegalArgumentException("Connector requires an axis-aligned face");normalAxis=a;}
        if(normalAxis<0)throw new IllegalArgumentException("Missing gate normal");for(int vertex=1;vertex<4;vertex++)for(int a=0;a<3;a++)if(Math.abs(source[vertex*8+5+a]-source[5+a])>1e-6)throw new IllegalArgumentException("Inconsistent gate normal");
        double[] e1=new double[3],e2=new double[3],low={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},high={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(int a=0;a<3;a++){e1[a]=source[8+a]-source[a];e2[a]=source[24+a]-source[a];for(int vertex=0;vertex<4;vertex++){low[a]=Math.min(low[a],source[vertex*8+a]);high[a]=Math.max(high[a],source[vertex*8+a]);}}
        double aa=dot(e1,e1),bb=dot(e2,e2),ab=dot(e1,e2),det=aa*bb-ab*ab;if(det<1e-20)throw new IllegalArgumentException("Degenerate gate face");
        double[] min={target.x0,target.y0,target.z0},max={target.x1,target.y1,target.z1};List<double[]> polygon=new ArrayList<>(4);double u0=Double.POSITIVE_INFINITY,v0=u0,u1=Double.NEGATIVE_INFINITY,v1=u1;
        for(int vertex=0;vertex<4;vertex++){
            double[] p=new double[8],d=new double[3];for(int a=0;a<3;a++){p[a]=high[a]-low[a]<1e-7?(source[5+a]<0?min[a]:max[a]):Math.abs(source[vertex*8+a]-low[a])<Math.abs(source[vertex*8+a]-high[a])?min[a]:max[a];d[a]=p[a]-source[a];}
            double d1=dot(d,e1),d2=dot(d,e2),s=(d1*bb-d2*ab)/det,t=(d2*aa-d1*ab)/det;
            p[3]=source[3]+s*(source[11]-source[3])+t*(source[27]-source[3]);p[4]=source[4]+s*(source[12]-source[4])+t*(source[28]-source[4]);
            for(int a=0;a<3;a++)p[5+a]=source[5+a];u0=Math.min(u0,p[3]);u1=Math.max(u1,p[3]);v0=Math.min(v0,p[4]);v1=Math.max(v1,p[4]);polygon.add(p);
        }
        int firstU=(int)Math.floor(u0),lastU=(int)Math.ceil(u1-1e-9)-1,firstV=(int)Math.floor(v0),lastV=(int)Math.ceil(v1-1e-9)-1;
        if(lastU-firstU>64||lastV-firstV>64||(long)(lastU-firstU+1)*(lastV-firstV+1)>128)throw new IllegalArgumentException("Gate connector UV capacity");
        int count=0;for(int u=firstU;u<=lastU;u++)for(int v=firstV;v<=lastV;v++){
            List<double[]> clipped=PanelTextureMapping.clip(PanelTextureMapping.clip(PanelTextureMapping.clip(PanelTextureMapping.clip(polygon,3,u,true),3,u+1,false),4,v,true),4,v+1,false);
            for(int at=1;at+1<clipped.size();at++)for(double[] p:new double[][]{clipped.get(0),clipped.get(at),clipped.get(at+1),clipped.get(at+1)}){
                sink.vertex(p[0],p[1],p[2],Math.max(0,Math.min(1,p[3]-u)),Math.max(0,Math.min(1,p[4]-v)),(float)p[5],(float)p[6],(float)p[7]);count++;
            }
        }return count;
    }
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
}
