package com.vandorlabs.client;

import com.vandorlabs.vehicle.EntityGroundVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;

/** Preserve the camera and seat offsets, translating only JourneyMap's viewing origin. */
public final class CruiseMapView {
    private static EntityGroundVehicle mounted() {
        Minecraft mc=Minecraft.getMinecraft();
        if (mc.player==null || mc.getRenderViewEntity()!=mc.player) return null;
        Entity root=mc.player.getRidingEntity();
        if (!(root instanceof EntityGroundVehicle)) return null;
        EntityGroundVehicle craft=(EntityGroundVehicle)root;
        return craft.cruise.active()?craft:null;
    }
    public static double x(double value) { EntityGroundVehicle c=mounted(); return c==null?value:value+(c.cruise.x()-c.posX); }
    public static double y(double value) { EntityGroundVehicle c=mounted(); return c==null?value:value+(c.flightAltitude()-c.posY); }
    public static double z(double value) { EntityGroundVehicle c=mounted(); return c==null?value:value+(c.cruise.z()-c.posZ); }
    public static double x(double value,EntityPlayer subject) { return subject==Minecraft.getMinecraft().player ? x(value) : value; }
    public static double y(double value,EntityPlayer subject) { return subject==Minecraft.getMinecraft().player ? y(value) : value; }
    public static double z(double value,EntityPlayer subject) { return subject==Minecraft.getMinecraft().player ? z(value) : value; }
    public static Vec3d viewPosition(Vec3d value) {
        EntityGroundVehicle c=mounted();
        return c==null?value:value.addVector(c.cruise.x()-c.posX,c.flightAltitude()-c.posY,c.cruise.z()-c.posZ);
    }
    private CruiseMapView(){}
}
