package com.vandorlabs.vehicle;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Opt-in coordinate probe. Mounted actor coordinates and terrain tracking have separate meanings. */
public final class CruiseCoordinateProbe {
    private static final boolean PROJECT = "projection".equals(System.getProperty("vandorlabs.cruiseCoordinateProbe"));

    private CruiseCoordinateProbe() {}

    static Vec3d passenger(Entity actor, Vec3d anchored) {
        EntityGroundVehicle craft=cruiseMount(actor);
        return craft==null?anchored:anchored.addVector(craft.cruise.x()-craft.posX,0,craft.cruise.z()-craft.posZ);
    }

    public static double trackingX(Entity actor) {
        EntityGroundVehicle craft=cruiseMount(actor);
        return craft==null?actor.posX:craft.posX;
    }

    public static double trackingZ(Entity actor) {
        EntityGroundVehicle craft=cruiseMount(actor);
        return craft==null?actor.posZ:craft.posZ;
    }

    /** The normal Forge position setter forces its destination chunk even for a mounted look packet. */
    public static void mountedRotation(EntityPlayerMP player,double x,double y,double z,float yaw,float pitch) {
        if (cruiseMount(player)==null) {
            player.setPositionAndRotation(x,y,z,yaw,pitch);
            return;
        }
        player.setPosition(x,y,z);
        player.prevPosX=x;
        player.prevPosY=y;
        player.prevPosZ=z;
        player.prevRotationYaw=yaw;
        player.rotationYaw=yaw%360;
        player.rotationPitch=player.prevRotationPitch=MathHelper.clamp(pitch,-90,90);
    }

    private static EntityGroundVehicle cruiseMount(Entity actor) {
        if (!PROJECT || !(actor instanceof EntityPlayer) || !(actor.getRidingEntity() instanceof EntityGroundVehicle)) return null;
        EntityGroundVehicle craft=(EntityGroundVehicle)actor.getRidingEntity();
        return craft.cruise.active()?craft:null;
    }
}
