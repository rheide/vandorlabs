package com.vandorlabs.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Complete world-space surface inputs without an additional world-light query. */
final class WorldSurface {
    private WorldSurface() { }

    static void vertex(BufferBuilder buffer, double x, double y, double z, double u, double v,
            float nx, float ny, float nz) {
        buffer.pos(x,y,z).color(255,255,255,255).tex(u,v)
                .lightmap((int)OpenGlHelper.lastBrightnessY,(int)OpenGlHelper.lastBrightnessX)
                .normal(nx,ny,nz).endVertex();
    }

    /** UVs stay attached to their positions when correcting an inward legacy face. */
    static void quad(BufferBuilder buffer, TextureAtlasSprite sprite, boolean reverse, double[]... points) {
        double[] a=points[0],b=points[1],c=points[2],d=points[3];
        rawQuad(buffer,reverse,
                a[0],a[1],a[2],sprite==null?a[3]:sprite.getInterpolatedU(a[3]),sprite==null?a[4]:sprite.getInterpolatedV(a[4]),
                b[0],b[1],b[2],sprite==null?b[3]:sprite.getInterpolatedU(b[3]),sprite==null?b[4]:sprite.getInterpolatedV(b[4]),
                c[0],c[1],c[2],sprite==null?c[3]:sprite.getInterpolatedU(c[3]),sprite==null?c[4]:sprite.getInterpolatedV(c[4]),
                d[0],d[1],d[2],sprite==null?d[3]:sprite.getInterpolatedU(d[3]),sprite==null?d[4]:sprite.getInterpolatedV(d[4]));
    }

    /** Allocation-free immediate surface path for dynamic boxes and control panels. */
    static void rawQuad(BufferBuilder buffer,boolean reverse,
            double x0,double y0,double z0,double u0,double v0,double x1,double y1,double z1,double u1,double v1,
            double x2,double y2,double z2,double u2,double v2,double x3,double y3,double z3,double u3,double v3) {
        // Diagonals also handle a triangle encoded as a quad with one repeated corner.
        double ux=x2-x0,uy=y2-y0,uz=z2-z0,vx=x3-x1,vy=y3-y1,vz=z3-z1;
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(length<1E-10)return;
        float sign=reverse?-1:1;
        float fx=(float)(sign*nx/length),fy=(float)(sign*ny/length),fz=(float)(sign*nz/length);
        vertex(buffer,x0,y0,z0,u0,v0,fx,fy,fz);
        if(reverse) {
            vertex(buffer,x3,y3,z3,u3,v3,fx,fy,fz);
            vertex(buffer,x2,y2,z2,u2,v2,fx,fy,fz);
            vertex(buffer,x1,y1,z1,u1,v1,fx,fy,fz);
        } else {
            vertex(buffer,x1,y1,z1,u1,v1,fx,fy,fz);
            vertex(buffer,x2,y2,z2,u2,v2,fx,fy,fz);
            vertex(buffer,x3,y3,z3,u3,v3,fx,fy,fz);
        }
    }
}
