package com.vandorlabs.vehicle;

import com.vandorlabs.core.CruiseTrackingTransformer;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;

/** Canonical chunk coordinates for Minecraft's tracking and save paths. */
public final class CruiseTracking {
    static final String SAVED_CRAFT="VandorCruiseCraft";
    private static final Set<EntityPlayerMP> READY=Collections.newSetFromMap(new WeakHashMap<>());

    private CruiseTracking() {}

    static boolean clientReady() { return CruiseTrackingTransformer.clientReady(); }

    static void ready(EntityPlayerMP player) { READY.add(player); }

    static boolean ready(EntityGroundVehicle craft) {
        if(!CruiseTrackingTransformer.serverReady()||craft.getPassengers().size()!=1)return false;
        Entity pilot=craft.getControllingPassenger();
        return pilot instanceof EntityPlayerMP&&READY.contains(pilot);
    }

    private static EntityGroundVehicle mount(Entity actor) {
        Entity vehicle=actor.getRidingEntity();
        if(!(vehicle instanceof EntityGroundVehicle))return null;
        EntityGroundVehicle craft=(EntityGroundVehicle)vehicle;
        return craft.cruise.projected()?craft:null;
    }

    private static EntityGroundVehicle tracked(Entity actor) {
        if(actor instanceof EntityGroundVehicle && ((EntityGroundVehicle)actor).cruise.projected())return (EntityGroundVehicle)actor;
        return mount(actor);
    }

    public static double trackingX(Entity actor) {
        EntityGroundVehicle craft=tracked(actor);
        return craft==null?actor.posX:craft.cruise.anchorX();
    }

    public static double trackingZ(Entity actor) {
        EntityGroundVehicle craft=tracked(actor);
        return craft==null?actor.posZ:craft.cruise.anchorZ();
    }

    public static double viewerX(Entity viewer,Entity tracked,long encoded) {
        return vehicleViewer(viewer,tracked)?encoded/4096.0:trackingX(viewer);
    }

    public static double viewerZ(Entity viewer,Entity tracked,long encoded) {
        return vehicleViewer(viewer,tracked)?encoded/4096.0:trackingZ(viewer);
    }

    private static boolean vehicleViewer(Entity viewer,Entity tracked) {
        // The tracker evaluates visibility against its previous packet position. An attached
        // rider must retain the aircraft while that reference is stale during cruise or arrival.
        return tracked instanceof EntityGroundVehicle&&tracked==viewer.getRidingEntity();
    }

    public static void writeAnchor(EntityPlayerMP rider,NBTTagCompound tag) {
        Entity vehicle=rider.getRidingEntity();
        NBTTagCompound custom=tag.getCompoundTag("ForgeData").copy();
        custom.removeTag(SAVED_CRAFT+"Most");
        custom.removeTag(SAVED_CRAFT+"Least");
        if(vehicle instanceof EntityGroundVehicle && ((EntityGroundVehicle)vehicle).cruise.active()) {
            EntityGroundVehicle craft=(EntityGroundVehicle)vehicle;
            if(craft.cruise.projected()) {
                writePosition(tag,craft.cruise.anchorX()+rider.posX-craft.posX,craft.cruise.anchorZ()+rider.posZ-craft.posZ);
            }
            custom.setUniqueId(SAVED_CRAFT,craft.getUniqueID());
        }
        if(!custom.hasNoTags() || tag.hasKey("ForgeData",10))tag.setTag("ForgeData",custom);
    }

    static void writePosition(NBTTagCompound tag,double x,double z) {
        NBTTagList pos=tag.getTagList("Pos",6);
        if(pos.tagCount()!=3)return;
        pos.set(0,new NBTTagDouble(x));
        pos.set(2,new NBTTagDouble(z));
    }

    /** The standard setter requests the geographic chunk even for a mounted look packet. */
    public static void mountedRotation(EntityPlayerMP player,double x,double y,double z,float yaw,float pitch) {
        if(mount(player)==null) {
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
}
