package com.vandorlabs.vehicle;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import com.vandorlabs.physics.RigidTransform;
import net.minecraft.util.EnumFacing;

/** A pose shared by the hull, riders and camera; simulation never reads visual poses. */
public final class VehiclePose {
    public final double x,y,z;
    public final float yaw;
    public final float pitch,bank;
    public final EnumFacing facing;
    public VehiclePose(double x,double y,double z,float yaw){this(x,y,z,yaw,0,EnumFacing.NORTH);}
    public VehiclePose(double x,double y,double z,float yaw,float pitch,EnumFacing facing){this(x,y,z,yaw,pitch,0,facing);}
    public VehiclePose(double x,double y,double z,float yaw,float pitch,float bank,EnumFacing facing){this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;this.bank=bank;this.facing=facing;}
    public VehiclePose interpolate(VehiclePose next,float partial) {
        return new VehiclePose(x+(next.x-x)*partial,y+(next.y-y)*partial,z+(next.z-z)*partial,
                yaw+MathHelper.wrapDegrees(next.yaw-yaw)*partial,pitch+(next.pitch-pitch)*partial,MathHelper.wrapDegrees(bank+MathHelper.wrapDegrees(next.bank-bank)*partial),next.facing);
    }
    public boolean tilted(){return pitch!=0||bank!=0;}

    /** Map mouse input from its reference heading into the current aircraft frame. */
    RigidTransform viewRotation(float referenceYaw){
        if(!tilted()&&yaw==referenceYaw)return RigidTransform.IDENTITY;
        double hx=facing.getFrontOffsetX(),hz=facing.getFrontOffsetZ();
        return RigidTransform.rotation(0,1,0,-yaw).then(RigidTransform.rotation(-hz,0,hx,pitch))
                .then(RigidTransform.rotation(hx,0,hz,bank)).then(RigidTransform.rotation(0,1,0,referenceYaw));
    }

    RigidTransform transform(double px,double pz){
        double hx=facing.getFrontOffsetX(),hz=facing.getFrontOffsetZ();
        RigidTransform yawPitch=RigidTransform.translation(x,y,z).then(RigidTransform.joint(px,0,pz,hx,hz,yaw,pitch));
        return bank==0?yawPitch:yawPitch.then(RigidTransform.translation(px,0,pz))
                .then(RigidTransform.rotation(hx,0,hz,bank)).then(RigidTransform.translation(-px,0,-pz));
    }
    public Vec3d world(Vec3d local,double px,double pz) {
        if(tilted()){double[] at=transform(px,pz).point(local.x,local.y,local.z);return new Vec3d(at[0],at[1],at[2]);}
        double c=Math.cos(Math.toRadians(yaw)),s=Math.sin(Math.toRadians(yaw)),lx=local.x-px,lz=local.z-pz;
        return new Vec3d(x+px+c*lx-s*lz,y+local.y,z+pz+s*lx+c*lz);
    }
    public Vec3d local(Vec3d absolute,double px,double pz) {
        if(tilted()){double[] at=transform(px,pz).inverse(absolute.x,absolute.y,absolute.z);return new Vec3d(at[0],at[1],at[2]);}
        double c=Math.cos(Math.toRadians(yaw)),s=Math.sin(Math.toRadians(yaw)),lx=absolute.x-x-px,lz=absolute.z-z-pz;
        return new Vec3d(px+c*lx+s*lz,absolute.y-y,pz-s*lx+c*lz);
    }
    public net.minecraft.util.math.AxisAlignedBB world(net.minecraft.util.math.AxisAlignedBB local,double px,double pz) {
        if(tilted())return VehicleFlightMotion.bounds(local,transform(px,pz));
        Vec3d center=world(new Vec3d((local.minX+local.maxX)/2,(local.minY+local.maxY)/2,(local.minZ+local.maxZ)/2),px,pz);
        double c=Math.abs(Math.cos(Math.toRadians(yaw))),s=Math.abs(Math.sin(Math.toRadians(yaw)));
        double dx=(c*(local.maxX-local.minX)+s*(local.maxZ-local.minZ))/2,dy=(local.maxY-local.minY)/2,
                dz=(s*(local.maxX-local.minX)+c*(local.maxZ-local.minZ))/2;
        return new net.minecraft.util.math.AxisAlignedBB(center.x-dx,center.y-dy,center.z-dz,center.x+dx,center.y+dy,center.z+dz);
    }
}
