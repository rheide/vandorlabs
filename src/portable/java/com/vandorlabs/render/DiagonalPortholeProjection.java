package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Current sloped aperture projection. Cold-build bounds/corner ownership remains with the caller. */
public class DiagonalPortholeProjection {
    public final int mode;
    public final boolean inverted;
    public final double lower,upper;
    public final double[] bounds;
    public final PanelCornerGeometry.Corner corner;
    public DiagonalPortholeProjection(int mode,boolean inverted,double lower,double upper,double[] bounds,PanelCornerGeometry.Corner corner){this.mode=mode;this.inverted=inverted;this.lower=lower;this.upper=upper;this.bounds=bounds;this.corner=corner;}

    public static <C,M> void quad(PanelQuadSink<C,M> sink,C context,M material,DiagonalPortholeProjection mesh,boolean reverse,double[]... vertices){
        if(mesh==null&&vertices.length==4){sink.quad(context,material,reverse,vertices[0],vertices[1],vertices[2],vertices[3]);return;}
        if(mesh==null){emit(sink,context,material,null,reverse,Arrays.asList(vertices));return;}
        double[] cuts=mesh.corner!=null?new double[]{0,2,4,8,12,14,16}:Double.isNaN(mesh.lower)&&Double.isNaN(mesh.upper)?new double[]{0,16}:new double[]{0,4,12,16};
        // Horizontal rims belong to exactly one band.
        boolean horizontal=true;for(double[] v:vertices)horizontal&=Math.abs(v[1]-vertices[0][1])<1e-8;
        if(horizontal){emit(sink,context,material,mesh,reverse,Arrays.asList(vertices));return;}
        for(int i=0;i<cuts.length-1;i++){
            List<double[]> polygon=clip(clip(Arrays.asList(vertices),cuts[i],true),cuts[i+1],false);
            if(polygon.size()>=3)emit(sink,context,material,mesh,reverse,polygon);
        }
    }
    private static List<double[]> clip(List<double[]> input,double y,boolean above){
        List<double[]> output=new ArrayList<double[]>();if(input.isEmpty())return output;double[] a=input.get(input.size()-1);
        for(double[] b:input){boolean ai=above?a[1]>=y:a[1]<=y,bi=above?b[1]>=y:b[1]<=y;if(ai!=bi){double t=(y-a[1])/(b[1]-a[1]);double[] v=new double[5];for(int k=0;k<5;k++)v[k]=a[k]+t*(b[k]-a[k]);output.add(v);}if(bi)output.add(b);a=b;}
        return output;
    }
    private static <C,M> void emit(PanelQuadSink<C,M> sink,C context,M material,DiagonalPortholeProjection mesh,boolean reverse,List<double[]> polygon){
        List<double[]> transformed=new ArrayList<double[]>();
        for(double[] source:polygon){double[] v=source.clone();if(mesh!=null){double y=v[1];double near=16*DiagonalWallGeometry.near(mesh.mode,mesh.inverted,y/16,mesh.lower,mesh.upper);if(mesh.corner!=null){double left=mesh.corner.left(near),right=mesh.corner.right(near);v[0]=left+v[0]*(right-left)/16;}v[2]=near+v[2]-6;if(mesh.mode==2){v[1]=v[2]+16*DiagonalWallGeometry.band(mesh.mode,mesh.inverted);v[2]=y;}}transformed.add(v);}
        List<double[]> clipped=DiagonalMeshClip.quadsKeepingContactCaps(mesh==null?null:mesh.bounds,transformed.toArray(new double[transformed.size()][]));boolean flipped=reverse^(mesh!=null&&mesh.mode==2);
        for(int i=0;i<clipped.size();i+=4)sink.quad(context,material,flipped,clipped.get(i),clipped.get(i+1),clipped.get(i+2),clipped.get(i+3));
    }
}
