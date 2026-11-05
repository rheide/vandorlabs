package com.vandorlabs.blocks;

import com.vandorlabs.render.ControlMountTransform;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Existing platform API over the shared supporting-face pedestal and tilt transform. */
public final class SignalControlMount extends ControlMountTransform<Vec3d,AxisAlignedBB> {
    private static final PointFactory<Vec3d> POINTS=Vec3d::new;
    private static final BoxFactory<AxisAlignedBB> BOXES=AxisAlignedBB::new;
    public static boolean valid(int height,int tilt,int direction){return com.vandorlabs.persistence.ControlMountSettings.valid(height,tilt,direction);}
    public SignalControlMount(EnumFacing facing,int rotation,int height,int tilt,int direction,AxisAlignedBB original){
        super(facing.getIndex(),rotation,height,tilt,direction,original.minX,original.minY,original.minZ,original.maxX,original.maxY,original.maxZ,POINTS,BOXES);
    }
    public Vec3d transform(Vec3d point){return super.transform(point.x,point.y,point.z);}
    public double depth(Vec3d point){return super.depth(point.x,point.y,point.z);}
    public Vec3d project(Vec3d point){return super.project(point.x,point.y,point.z);}
    public AxisAlignedBB bounds(AxisAlignedBB box){return super.bounds(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);}
    public java.util.List<AxisAlignedBB> wedgeBoxes(AxisAlignedBB support){return super.wedgeBoxes(support.minX,support.minY,support.minZ,support.maxX,support.maxY,support.maxZ);}
    public static int readTilt(net.minecraft.nbt.NBTTagCompound tag){
        return com.vandorlabs.persistence.ControlMountSettings.migratedTilt(tag.getInteger("BaseTilt"),tag.hasKey("ControlMountVersion"));
    }
}
