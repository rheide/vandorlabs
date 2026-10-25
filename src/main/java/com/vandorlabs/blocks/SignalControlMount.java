package com.vandorlabs.blocks;

import com.vandorlabs.render.ControlMount;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Native vectors and bounds over the shared authored control mounting transform. */
public final class SignalControlMount {
    private final ControlMount delegate;
    public static boolean valid(int height,int tilt,int direction){return ControlMount.valid(height,tilt,direction);}
    public SignalControlMount(EnumFacing facing,int rotation,int height,int tilt,int direction,AxisAlignedBB original){delegate=new ControlMount(facing.getIndex(),rotation,height,tilt,direction,box(original));}
    private static ControlMount.Point point(Vec3d p){return new ControlMount.Point(p.x,p.y,p.z);}private static Vec3d vector(ControlMount.Point p){return new Vec3d(p.x,p.y,p.z);}
    private static ControlMount.Box box(AxisAlignedBB b){return new ControlMount.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}private static AxisAlignedBB bounds(ControlMount.Box b){return new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    public Vec3d transform(Vec3d p){return vector(delegate.transform(point(p)));}public Vec3d project(Vec3d p){return vector(delegate.project(point(p)));}public double depth(Vec3d p){return delegate.depth(point(p));}public AxisAlignedBB bounds(AxisAlignedBB b){return bounds(delegate.bounds(box(b)));}
    public java.util.List<AxisAlignedBB> wedgeBoxes(AxisAlignedBB support){java.util.List<AxisAlignedBB> result=new java.util.ArrayList<>();for(ControlMount.Box b:delegate.wedgeBoxes(box(support)))result.add(bounds(b));return result;}
    public static int readTilt(net.minecraft.nbt.NBTTagCompound tag){return ControlMount.migratedTilt(tag.getInteger("BaseTilt"),tag.hasKey("ControlMountVersion"));}
}
