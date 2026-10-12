package com.vandorlabs.vehicle;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** A pose shared by the hull, riders and camera; simulation never reads visual poses. */
public final class VehiclePose {
    public final double x,y,z;
    public final float yaw;
    public VehiclePose(double x,double y,double z,float yaw){this.x=x;this.y=y;this.z=z;this.yaw=yaw;}
    public VehiclePose interpolate(VehiclePose next,float partial) {
        return new VehiclePose(x+(next.x-x)*partial,y+(next.y-y)*partial,z+(next.z-z)*partial,
                yaw+MathHelper.wrapDegrees(next.yaw-yaw)*partial);
    }
    public Vec3d world(Vec3d local,double px,double pz) {
        double c=Math.cos(Math.toRadians(yaw)),s=Math.sin(Math.toRadians(yaw)),lx=local.x-px,lz=local.z-pz;
        return new Vec3d(x+px+c*lx-s*lz,y+local.y,z+pz+s*lx+c*lz);
    }
    public Vec3d local(Vec3d absolute,double px,double pz) {
        double c=Math.cos(Math.toRadians(yaw)),s=Math.sin(Math.toRadians(yaw)),lx=absolute.x-x-px,lz=absolute.z-z-pz;
        return new Vec3d(px+c*lx+s*lz,absolute.y-y,pz-s*lx+c*lz);
    }
    public net.minecraft.util.math.AxisAlignedBB world(net.minecraft.util.math.AxisAlignedBB local,double px,double pz) {
        Vec3d center=world(new Vec3d((local.minX+local.maxX)/2,(local.minY+local.maxY)/2,(local.minZ+local.maxZ)/2),px,pz);
        double c=Math.abs(Math.cos(Math.toRadians(yaw))),s=Math.abs(Math.sin(Math.toRadians(yaw)));
        double dx=(c*(local.maxX-local.minX)+s*(local.maxZ-local.minZ))/2,dy=(local.maxY-local.minY)/2,
                dz=(s*(local.maxX-local.minX)+c*(local.maxZ-local.minZ))/2;
        return new net.minecraft.util.math.AxisAlignedBB(center.x-dx,center.y-dy,center.z-dz,center.x+dx,center.y+dy,center.z+dz);
    }
}
