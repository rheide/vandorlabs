package com.vandorlabs.client;

import com.vandorlabs.render.DiagonalWallGeometry;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Splits porthole surfaces at the same end transitions as solid diagonal walls. */
final class DiagonalPortholeMesh {
    final int mode;
    final boolean inverted;
    final double lower, upper;
    final double[] bounds;
    DiagonalPortholeMesh(int mode, boolean inverted, double lower, double upper, double[] bounds) {
        this.mode=mode; this.inverted=inverted; this.lower=lower; this.upper=upper; this.bounds=bounds;
    }

    static void quad(BufferBuilder buf, TextureAtlasSprite sprite, boolean textured,
            DiagonalPortholeMesh mesh, double[]... vertices) {
        quad(buf,sprite,textured,mesh,false,vertices);
    }

    static void quad(BufferBuilder buf, TextureAtlasSprite sprite, boolean textured,
            DiagonalPortholeMesh mesh, boolean reverse, double[]... vertices) {
        if(mesh==null && vertices.length==4) {
            WorldSurface.quad(buf,sprite,reverse,vertices);
            return;
        }
        if (mesh==null) { emit(buf,sprite,textured,null,reverse,Arrays.asList(vertices)); return; }
        double[] cuts=Double.isNaN(mesh.lower) && Double.isNaN(mesh.upper)
                ? new double[]{0,16} : new double[]{0,4,12,16};
        // Horizontal rims belong to exactly one band.
        boolean horizontal=true;
        for(double[] v:vertices) horizontal &= Math.abs(v[1]-vertices[0][1])<1e-8;
        if(horizontal) { emit(buf,sprite,textured,mesh,reverse,Arrays.asList(vertices)); return; }
        for(int i=0;i<cuts.length-1;i++) {
            List<double[]> polygon=clip(clip(Arrays.asList(vertices),cuts[i],true),cuts[i+1],false);
            if(polygon.size()>=3) emit(buf,sprite,textured,mesh,reverse,polygon);
        }
    }

    private static List<double[]> clip(List<double[]> input,double y,boolean above) {
        List<double[]> output=new ArrayList<>();
        if(input.isEmpty()) return output;
        double[] a=input.get(input.size()-1);
        for(double[] b:input) {
            boolean ai=above?a[1]>=y:a[1]<=y, bi=above?b[1]>=y:b[1]<=y;
            if(ai!=bi) {
                double t=(y-a[1])/(b[1]-a[1]); double[] v=new double[5];
                for(int k=0;k<5;k++)v[k]=a[k]+t*(b[k]-a[k]);
                output.add(v);
            }
            if(bi)output.add(b);
            a=b;
        }
        return output;
    }

    private static void emit(BufferBuilder buf,TextureAtlasSprite sprite,boolean textured,
            DiagonalPortholeMesh mesh,boolean reverse,List<double[]> polygon) {
        List<double[]> transformed=new ArrayList<>();
        for(double[] source:polygon) {
            double[] v=source.clone();
            if(mesh!=null) {
                double y=v[1];
                v[2]=16*DiagonalWallGeometry.near(mesh.mode,mesh.inverted,y/16,mesh.lower,mesh.upper)+v[2]-6;
                if(mesh.mode==2) { v[1]=v[2]+16*DiagonalWallGeometry.band(mesh.mode,mesh.inverted);v[2]=y; }
            }
            transformed.add(v);
        }
        List<double[]> clipped=com.vandorlabs.render.DiagonalMeshClip.quads(mesh==null?null:mesh.bounds,
                transformed.toArray(new double[transformed.size()][]));
        boolean flipped=reverse ^ (mesh!=null && mesh.mode==2);
        for(int i=0;i<clipped.size();i+=4)
            WorldSurface.quad(buf,sprite,flipped,clipped.get(i),clipped.get(i+1),clipped.get(i+2),clipped.get(i+3));
    }
}
