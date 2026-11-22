package com.vandorlabs.vehicle;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.MathHelper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.Vec3d;

/** Opt-in coordinate probe. Mounted actor coordinates and terrain tracking have separate meanings. */
public final class CruiseCoordinateProbe {
    private static final String MODE = System.getProperty("vandorlabs.cruiseCoordinateProbe","");
    static final boolean FULL = MODE.equals("craft-projection");
    private static final boolean PROJECT = FULL || MODE.equals("projection");

    private CruiseCoordinateProbe() {}

    static Vec3d passenger(Entity actor, Vec3d anchored) {
        EntityGroundVehicle craft=cruiseMount(actor);
        return craft==null||FULL?anchored:anchored.addVector(craft.cruise.x()-craft.posX,0,craft.cruise.z()-craft.posZ);
    }

    public static double trackingX(Entity actor) {
        EntityGroundVehicle craft=trackingCraft(actor);
        return craft==null?actor.posX:FULL?craft.cruise.storageX():craft.posX;
    }

    public static double trackingZ(Entity actor) {
        EntityGroundVehicle craft=trackingCraft(actor);
        return craft==null?actor.posZ:FULL?craft.cruise.storageZ():craft.posZ;
    }

    public static double viewerX(Entity viewer,Entity tracked) {
        return FULL && tracked==cruiseMount(viewer)?viewer.posX:trackingX(viewer);
    }

    public static double viewerZ(Entity viewer,Entity tracked) {
        return FULL && tracked==cruiseMount(viewer)?viewer.posZ:trackingZ(viewer);
    }

    public static void writeAnchor(EntityPlayerMP player,NBTTagCompound tag) {
        EntityGroundVehicle craft=cruiseMount(player);
        if(FULL && craft!=null) writePosition(tag,
            craft.cruise.storageX()+player.posX-craft.posX,craft.cruise.storageZ()+player.posZ-craft.posZ);
    }

    static void writePosition(NBTTagCompound tag,double x,double z) {
        NBTTagList pos=tag.getTagList("Pos",6);
        if(pos.tagCount()!=3) return;
        pos.set(0,new NBTTagDouble(x));
        pos.set(2,new NBTTagDouble(z));
    }

    private static EntityGroundVehicle trackingCraft(Entity actor) {
        if(FULL && actor instanceof EntityGroundVehicle && ((EntityGroundVehicle)actor).cruise.active()) return (EntityGroundVehicle)actor;
        return cruiseMount(actor);
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
