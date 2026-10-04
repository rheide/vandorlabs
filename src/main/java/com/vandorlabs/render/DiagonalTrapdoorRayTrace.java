package com.vandorlabs.render;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Exact face-order intersection without temporary vectors for each face. */
public final class DiagonalTrapdoorRayTrace {
    private DiagonalTrapdoorRayTrace() { }
    public static RayTraceResult trace(double[][] corners,BlockPos pos,Vec3d start,Vec3d end) {
        double ox=start.x-pos.getX(),oy=start.y-pos.getY(),oz=start.z-pos.getZ();
        double dx=end.x-start.x,dy=end.y-start.y,dz=end.z-start.z;
        double nearest=Double.POSITIVE_INFINITY;EnumFacing hitFace=null;
        for(int[] face:TrapdoorGeometry.FACES) {
            double[] a=corners[face[0]],b=corners[face[1]],c=corners[face[3]];
            double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
            double wx=c[0]-a[0],wy=c[1]-a[1],wz=c[2]-a[2];
            double nx=uy*wz-uz*wy,ny=uz*wx-ux*wz,nz=ux*wy-uy*wx;
            double denominator=nx*dx+ny*dy+nz*dz;
            if(Math.abs(denominator)<1e-9)continue;
            double t=(nx*(a[0]-ox)+ny*(a[1]-oy)+nz*(a[2]-oz))/denominator;
            if(t<0 || t>1 || t>=nearest)continue;
            double hx=ox+dx*t-a[0],hy=oy+dy*t-a[1],hz=oz+dz*t-a[2];
            double uu=ux*ux+uy*uy+uz*uz,ww=wx*wx+wy*wy+wz*wz,uw=ux*wx+uy*wy+uz*wz;
            double hu=hx*ux+hy*uy+hz*uz,hw=hx*wx+hy*wy+hz*wz,det=uu*ww-uw*uw;
            double s=(hu*ww-hw*uw)/det,r=(hw*uu-hu*uw)/det;
            if(s>=-1e-8 && s<=1+1e-8 && r>=-1e-8 && r<=1+1e-8) {
                nearest=t;hitFace=EnumFacing.getFacingFromVector((float)nx,(float)ny,(float)nz);
            }
        }
        return hitFace==null?null:new RayTraceResult(new Vec3d(start.x+dx*nearest,start.y+dy*nearest,start.z+dz*nearest),hitFace,pos);
    }
}
