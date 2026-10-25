package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

/** Panel repeat/stretch arithmetic independent of atlas placement and vertex packing. */
public final class PanelTextureMapping {
    public static class Layout {
        public final int column,row,columns,rows;
        public final boolean flipU,flipV;
        public Layout(int column,int row,int columns,int rows){this(column,row,columns,rows,false,false);}
        public Layout(int column,int row,int columns,int rows,boolean flipU,boolean flipV){this.column=column;this.row=row;this.columns=columns;this.rows=rows;this.flipU=flipU;this.flipV=flipV;}
        public String key(){return column+":"+row+":"+columns+":"+rows+":"+flipU+":"+flipV;}
    }
    private PanelTextureMapping(){}
    /** The vertex-preserving legacy glassTexture path; atlas normalization stays in its backend. */
    public static double stretchU(double u,Layout layout,double borderX){if(layout.flipU)u=1-u;u=(layout.column+u-borderX)/(layout.columns-2*borderX);return Math.max(0,Math.min(1,u));}
    public static double stretchV(double v,Layout layout,double borderY){if(layout.flipV)v=1-v;v=(layout.rows-1-layout.row+v-borderY)/(layout.rows-2*borderY);return Math.max(0,Math.min(1,v));}
    /**
     * Input positions are in authored pixels; UVs are normalized to the selected sprite.
     * Returns triangle quads (the fourth point duplicates the third), with UVs in [0,1].
     * A null result means a mixed-sprite/frame quad must pass through unchanged.
     * The input is never mutated; returned records belong to the caller.
     */
    public static List<double[][]> map(double[][] quad,Layout layout,double borderX,double borderY,int mode,double aspect){
        List<double[]> polygon=new ArrayList<>();boolean selected=true;
        double minU=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,minV=minU,maxV=maxU,unitU=1,unitV=1;
        for(int i=0;i<4;i++){
            double[] p=quad[i].clone();
            if(layout.flipU)p[3]=1-p[3];if(layout.flipV)p[4]=1-p[4];
            selected &= p[3]>=-1e-4&&p[3]<=1.0001&&p[4]>=-1e-4&&p[4]<=1.0001;
            minU=Math.min(minU,p[3]);maxU=Math.max(maxU,p[3]);minV=Math.min(minV,p[4]);maxV=Math.max(maxV,p[4]);polygon.add(p);
        }
        if(!selected)return null;
        for(double[] a:polygon)for(double[] b:polygon){double du=Math.abs(a[3]-b[3]),dv=Math.abs(a[4]-b[4]),length=Math.sqrt((a[0]-b[0])*(a[0]-b[0])+(a[1]-b[1])*(a[1]-b[1])+(a[2]-b[2])*(a[2]-b[2]))/16;
            if(du>1e-6&&dv<1e-6)unitU=length/du;if(dv>1e-6&&du<1e-6)unitV=length/dv;
        }
        boolean fit=TextureLayoutMode.fit(mode,(layout.columns-2*borderX)*unitU,(layout.rows-2*borderY)*unitV,aspect);
        minU=minV=Double.POSITIVE_INFINITY;maxU=maxV=Double.NEGATIVE_INFINITY;
        for(double[] p:polygon){
            p[3]=fit?(layout.column+p[3]-borderX)/(layout.columns-2*borderX):TextureLayoutMode.tileU((layout.column+p[3])*unitU,aspect);
            p[4]=fit?(layout.rows-1-layout.row+p[4]-borderY)/(layout.rows-2*borderY):TextureLayoutMode.tileV((layout.rows-1-layout.row+p[4])*unitV,aspect);
            if(fit){p[3]=Math.max(0,Math.min(1,p[3]));p[4]=Math.max(0,Math.min(1,p[4]));}
            minU=Math.min(minU,p[3]);maxU=Math.max(maxU,p[3]);minV=Math.min(minV,p[4]);maxV=Math.max(maxV,p[4]);
        }
        int u0=fit?0:(int)Math.floor(minU),u1=fit?0:(int)Math.ceil(maxU-1e-8)-1,v0=fit?0:(int)Math.floor(minV),v1=fit?0:(int)Math.ceil(maxV-1e-8)-1;
        List<double[][]> out=new ArrayList<>();
        for(int u=u0;u<=u1;u++)for(int v=v0;v<=v1;v++){
            List<double[]> clipped=PolygonClip.clip(PolygonClip.clip(PolygonClip.clip(PolygonClip.clip(polygon,3,u,true),3,u+1,false),4,v,true),4,v+1,false);
            for(int i=1;i+1<clipped.size();i++){
                double[][] triangle={clipped.get(0).clone(),clipped.get(i).clone(),clipped.get(i+1).clone(),clipped.get(i+1).clone()};
                for(double[] p:triangle){p[3]=Math.max(0,Math.min(1,p[3]-u));p[4]=Math.max(0,Math.min(1,p[4]-v));}
                out.add(triangle);
            }
        }
        return out;
    }
}
