package com.vandorlabs.shipsystems;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Proper quarter-turn rotations around the placement cell, with local Y pointing off the support. */
public final class MountFrame {
    public final EnumFacing up, right, back;
    public MountFrame(EnumFacing facing, EnumFacing mount) {
        up=mount;
        back=mount.getAxis()==EnumFacing.Axis.Y ? facing.getOpposite() : EnumFacing.UP;
        Vec3i a=up.getDirectionVec(),b=back.getDirectionVec();
        Vec3i cross=a.crossProduct(b);
        right=EnumFacing.getFacingFromVector(cross.getX(),cross.getY(),cross.getZ());
    }
    public BlockPos cell(BlockPos origin,int x,int y,int z) {
        return origin.offset(right,x).offset(up,y).offset(back,z);
    }
    public Vec3d point(Vec3d v) {
        return new Vec3d(.5+right.getFrontOffsetX()*(v.x-.5)+up.getFrontOffsetX()*(v.y-.5)+back.getFrontOffsetX()*(v.z-.5),
                .5+right.getFrontOffsetY()*(v.x-.5)+up.getFrontOffsetY()*(v.y-.5)+back.getFrontOffsetY()*(v.z-.5),
                .5+right.getFrontOffsetZ()*(v.x-.5)+up.getFrontOffsetZ()*(v.y-.5)+back.getFrontOffsetZ()*(v.z-.5));
    }
}
