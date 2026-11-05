package com.vandorlabs.render;

/** Scalar current panel normals/winding. Hosts supply atlas interpolation, light and vertex storage. */
public final class PanelVertexEmitter {
    public interface RawSink<C>{void vertex(C context,double x,double y,double z,double u,double v,float nx,float ny,float nz);}
    public interface PixelSink<C,M>{void vertex(C context,M material,double x,double y,double z,double u,double v,float nx,float ny,float nz);}
    public static <C> void raw(RawSink<C> sink,C buffer,boolean reverse,
            double x0,double y0,double z0,double u0,double v0,double x1,double y1,double z1,double u1,double v1,
            double x2,double y2,double z2,double u2,double v2,double x3,double y3,double z3,double u3,double v3){
        // Diagonals also handle a triangle encoded as a quad with one repeated corner.
        double ux=x2-x0,uy=y2-y0,uz=z2-z0,vx=x3-x1,vy=y3-y1,vz=z3-z1;
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(length<1E-10)return;
        float sign=reverse?-1:1;
        float fx=(float)(sign*nx/length),fy=(float)(sign*ny/length),fz=(float)(sign*nz/length);
        sink.vertex(buffer,x0,y0,z0,u0,v0,fx,fy,fz);
        if(reverse) {
            sink.vertex(buffer,x3,y3,z3,u3,v3,fx,fy,fz);
            sink.vertex(buffer,x2,y2,z2,u2,v2,fx,fy,fz);
            sink.vertex(buffer,x1,y1,z1,u1,v1,fx,fy,fz);
        } else {
            sink.vertex(buffer,x1,y1,z1,u1,v1,fx,fy,fz);
            sink.vertex(buffer,x2,y2,z2,u2,v2,fx,fy,fz);
            sink.vertex(buffer,x3,y3,z3,u3,v3,fx,fy,fz);
        }
    }
    public static <C,M> void normal(PixelSink<C,M> sink,C context,M material,int sign,double halfOffset,
            double[] clip,double[] a,double[] b,double[] c,double[] d){
        if (!Double.isNaN(halfOffset)) {
            // Bake the half-height reflection into positions before shader
            // tangent/normal generation, preserving outward face winding.
            for (double[] p : new double[][]{a,b,c,d}) {
                double y = p[1]; p[1] = p[2] + halfOffset; p[2] = y;
            }
            sign = -sign;
        }
        // Diagonals retain a triangle when an arm collapses at a slope end.
        double ux=c[0]-a[0], uy=c[1]-a[1], uz=c[2]-a[2];
        double vx=d[0]-b[0], vy=d[1]-b[1], vz=d[2]-b[2];
        double nx=sign*(uy*vz-uz*vy), ny=sign*(uz*vx-ux*vz),
                nz=sign*(ux*vy-uy*vx);
        double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if (length<1.0E-7) return;
        float fx=(float)(nx/length), fy=(float)(ny/length), fz=(float)(nz/length);
        // Shader integrations may derive normals from winding. Submit the
        // same orientation as the explicit normal, including the roof faces.
        for (double[] p : com.vandorlabs.render.DiagonalMeshClip.quadsKeepingContactCaps(clip,
                sign < 0 ? new double[][]{a,d,c,b} : new double[][]{a,b,c,d}))
            sink.vertex(context,material,p[0],p[1],p[2],Math.max(0,Math.min(16,p[3])),Math.max(0,Math.min(16,p[4])),fx,fy,fz);
    }
    private PanelVertexEmitter(){}
}
