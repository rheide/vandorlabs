package com.vandorlabs.blocks;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

/** Local face coordinates shared by selection bounds and live slot contents. */
public final class TokenSlotGeometry {
    private TokenSlotGeometry(){}
    public static Vec3d point(EnumFacing facing,int rotation,double x,double y,double depth){
        Vec3d normal=new Vec3d(facing.getDirectionVec()),u,v;
        switch(facing){
            case SOUTH:u=new Vec3d(-1,0,0);v=new Vec3d(0,1,0);break;
            case EAST:u=new Vec3d(0,0,1);v=new Vec3d(0,1,0);break;
            case WEST:u=new Vec3d(0,0,-1);v=new Vec3d(0,1,0);break;
            case UP:u=new Vec3d(1,0,0);v=new Vec3d(0,0,1);break;
            case DOWN:u=new Vec3d(1,0,0);v=new Vec3d(0,0,-1);break;
            default:u=new Vec3d(1,0,0);v=new Vec3d(0,1,0);
        }
        if(facing.getAxis()==EnumFacing.Axis.Y)for(int i=0;i<rotation;i++){Vec3d old=u;u=v;v=old.scale(-1);}
        return new Vec3d(.5,.5,.5).subtract(normal.scale(.5-depth)).add(u.scale(x)).add(v.scale(y));
    }
}
