package com.vandorlabs.blocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** One attachment frame shared by reader hit testing and item rendering. */
public final class MachineMountSurface {
    public final Vec3d center,u,v,normal;
    public final double width,height;
    public MachineMountSurface(Vec3d center,EnumFacing facing,double width,double height){
        this.center=center;this.width=width;this.height=height;
        normal=new Vec3d(facing.getDirectionVec());u=new Vec3d(-facing.rotateY().getFrontOffsetX(),0,-facing.rotateY().getFrontOffsetZ());v=new Vec3d(0,1,0);
    }
    public boolean hit(EntityPlayer player){
        double reach=player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        Vec3d start=player.getPositionEyes(1),delta=Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(reach);
        double divisor=delta.dotProduct(normal);if(divisor>=-1e-8)return false;
        double t=center.subtract(start).dotProduct(normal)/divisor;if(t<0||t>1)return false;
        Vec3d local=start.add(delta.scale(t)).subtract(center);
        return Math.abs(local.dotProduct(u))<=width/2+.035&&Math.abs(local.dotProduct(v))<=height/2+.035;
    }
    public static Vec3d rotate(Vec3d p,EnumFacing facing,double width,double depth){
        switch(facing){case EAST:return new Vec3d(depth-p.z,p.y,p.x);case SOUTH:return new Vec3d(width-p.x,p.y,depth-p.z);case WEST:return new Vec3d(p.z,p.y,width-p.x);default:return p;}
    }
}
