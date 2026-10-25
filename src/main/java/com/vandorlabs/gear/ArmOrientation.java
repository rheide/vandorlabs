package com.vandorlabs.gear;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Exact orthogonal basis from the authored downward arm to its mounting face. */
public final class ArmOrientation {
    private ArmOrientation() { }
    public static BlockPos vector(EnumFacing face,BlockPos p){
        Vec3d v=vector(face,p.getX(),p.getY(),p.getZ());
        return new BlockPos(v.x,v.y,v.z);
    }
    public static Vec3d vector(EnumFacing face,double x,double y,double z){
        switch(face){
            case UP:return new Vec3d(x,-y,-z);
            case EAST:return new Vec3d(-y,x,z);
            case WEST:return new Vec3d(y,-x,z);
            case NORTH:return new Vec3d(x,-z,y);
            case SOUTH:return new Vec3d(x,z,-y);
            default:return new Vec3d(x,y,z);
        }
    }
    public static Vec3d point(EnumFacing face,double x,double y,double z){return vector(face,x-.5,y-.5,z-.5).addVector(.5,.5,.5);}
    public static BlockPos inverse(EnumFacing face,BlockPos p){
        switch(face){case EAST:return vector(EnumFacing.WEST,p);case WEST:return vector(EnumFacing.EAST,p);case NORTH:return vector(EnumFacing.SOUTH,p);case SOUTH:return vector(EnumFacing.NORTH,p);default:return vector(face,p);}
    }
    public static AxisAlignedBB box(EnumFacing face,AxisAlignedBB b){
        double lx=Double.POSITIVE_INFINITY,ly=lx,lz=lx,hx=Double.NEGATIVE_INFINITY,hy=hx,hz=hx;
        for(int n=0;n<8;n++){Vec3d v=point(face,(n&1)==0?b.minX:b.maxX,(n&2)==0?b.minY:b.maxY,(n&4)==0?b.minZ:b.maxZ);lx=Math.min(lx,v.x);ly=Math.min(ly,v.y);lz=Math.min(lz,v.z);hx=Math.max(hx,v.x);hy=Math.max(hy,v.y);hz=Math.max(hz,v.z);}
        return new AxisAlignedBB(lx,ly,lz,hx,hy,hz);
    }
}
