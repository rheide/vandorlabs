package com.vandorlabs.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.ArrayList;
import java.util.List;

/** Build-time merging only: retain material, winding, plane and affine UV mapping. */
final class DiagonalQuadMerger {
    private static final double EPS=1E-7;
    static final class Face {
        final TextureAtlasSprite sprite;
        final double[][] points;
        Face(TextureAtlasSprite sprite,double[][] points){this.sprite=sprite;this.points=points;}
    }
    private final List<Face> faces=new ArrayList<>();
    void add(Face face) {
        // The emitter visits each side once per strip. Usually the matching side
        // is only a few entries back; failed candidates allocate no geometry.
        for(int i=faces.size()-1;i>=0;i--) {
            Face joined=merge(faces.get(i),face);
            if(joined!=null){faces.remove(i);face=joined;i=faces.size();}
        }
        faces.add(face);
    }
    List<Face> faces(){return faces;}
    private static Face merge(Face a,Face b) {
        if(a.sprite!=b.sprite)return null;
        double[][] p=a.points,q=b.points;
        for(int n=5;n<8;n++)if(p[0][n]!=q[0][n])return null;
        for(int i=0;i<4;i++)for(int j=0;j<4;j++) {
            if(!same(p[i],q[(j+1)&3]) || !same(p[(i+1)&3],q[j]))continue;
            if(!affine(p,q))return null;
            double[][] ring={p[(i+1)&3],p[(i+2)&3],p[(i+3)&3],p[i],q[(j+2)&3],q[(j+3)&3]};
            int size=6;
            for(int k=0;k<size && size>4;) {
                if(collinear(ring[(k+size-1)%size],ring[k],ring[(k+1)%size])) {
                    System.arraycopy(ring,k+1,ring,k,size-k-1);size--;k=0;
                }else k++;
            }
            if(size!=4 || !convex(ring,p))return null;
            double original=area(p)+area(q),merged=area(ring);
            if(Math.abs(original-merged)>EPS)return null;
            return new Face(a.sprite,new double[][]{ring[0],ring[1],ring[2],ring[3]});
        }
        return null;
    }
    private static boolean same(double[] a,double[] b) {
        for(int i=0;i<8;i++)if(Math.abs(a[i]-b[i])>EPS)return false;
        return true;
    }
    private static boolean affine(double[][] p,double[][] q) {
        double[] a=p[0],b=p[1],d=p[3];
        double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
        double vx=d[0]-a[0],vy=d[1]-a[1],vz=d[2]-a[2];
        double uu=ux*ux+uy*uy+uz*uz,vv=vx*vx+vy*vy+vz*vz,uv=ux*vx+uy*vy+uz*vz;
        double determinant=uu*vv-uv*uv;
        if(determinant<1E-14)return false;
        for(int i=0;i<8;i++) {
            double[] c=i<4?p[i]:q[i-4];
            for(int n=5;n<8;n++)if(c[n]!=a[n])return false;
            double x=c[0]-a[0],y=c[1]-a[1],z=c[2]-a[2];
            double cu=x*ux+y*uy+z*uz,cv=x*vx+y*vy+z*vz;
            double s=(cu*vv-cv*uv)/determinant,t=(cv*uu-cu*uv)/determinant;
            for(int n=0;n<5;n++)if(Math.abs(a[n]+s*(b[n]-a[n])+t*(d[n]-a[n])-c[n])>EPS)return false;
        }
        return true;
    }
    private static boolean collinear(double[] a,double[] b,double[] c) {
        double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
        double vx=c[0]-b[0],vy=c[1]-b[1],vz=c[2]-b[2];
        double x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx;
        return x*x+y*y+z*z<1E-20 && ux*vx+uy*vy+uz*vz>=0;
    }
    private static boolean convex(double[][] p,double[][] original) {
        double[] a=original[0],b=original[1],d=original[3];
        double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
        double vx=d[0]-a[0],vy=d[1]-a[1],vz=d[2]-a[2];
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        for(int i=0;i<4;i++) {
            a=p[i];b=p[(i+1)&3];d=p[(i+2)&3];
            ux=b[0]-a[0];uy=b[1]-a[1];uz=b[2]-a[2];
            vx=d[0]-b[0];vy=d[1]-b[1];vz=d[2]-b[2];
            if((uy*vz-uz*vy)*nx+(uz*vx-ux*vz)*ny+(ux*vy-uy*vx)*nz<=1E-14)return false;
        }
        return true;
    }
    private static double area(double[][] p) {
        double x=0,y=0,z=0;
        for(int i=1;i<3;i++) {
            double ux=p[i][0]-p[0][0],uy=p[i][1]-p[0][1],uz=p[i][2]-p[0][2];
            double vx=p[i+1][0]-p[0][0],vy=p[i+1][1]-p[0][1],vz=p[i+1][2]-p[0][2];
            x+=uy*vz-uz*vy;y+=uz*vx-ux*vz;z+=ux*vy-uy*vx;
        }
        return Math.sqrt(x*x+y*y+z*z)/2;
    }
}
