package com.vandorlabs.neoforge.gear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Exact Source ArmOrientation basis. Local -Y always runs away from the mounting surface. */
public final class NativeGearOrientation {
    public static Vec3 vector(Direction face,double x,double y,double z){return new Vec3(com.vandorlabs.gear.core.GearMountFrame.x(face.ordinal(),x,y,z),com.vandorlabs.gear.core.GearMountFrame.y(face.ordinal(),x,y,z),com.vandorlabs.gear.core.GearMountFrame.z(face.ordinal(),x,y,z));}
    public static BlockPos vector(Direction face,BlockPos point){var v=vector(face,point.getX(),point.getY(),point.getZ());return new BlockPos((int)v.x,(int)v.y,(int)v.z);}
    public static BlockPos inverse(Direction face,BlockPos point){return vector(switch(face){case EAST->Direction.WEST;case WEST->Direction.EAST;case NORTH->Direction.SOUTH;case SOUTH->Direction.NORTH;default->face;},point);}
    public static Vec3 point(Direction face,double x,double y,double z){return vector(face,x-.5,y-.5,z-.5).add(.5,.5,.5);}
    public static AABB box(Direction face,AABB box){double lx=Double.POSITIVE_INFINITY,ly=lx,lz=lx,hx=Double.NEGATIVE_INFINITY,hy=hx,hz=hx;
        for(int n=0;n<8;n++){var p=point(face,(n&1)==0?box.minX:box.maxX,(n&2)==0?box.minY:box.maxY,(n&4)==0?box.minZ:box.maxZ);lx=Math.min(lx,p.x);ly=Math.min(ly,p.y);lz=Math.min(lz,p.z);hx=Math.max(hx,p.x);hy=Math.max(hy,p.y);hz=Math.max(hz,p.z);}
        return new AABB(lx,ly,lz,hx,hy,hz);}
    private NativeGearOrientation(){}
}
