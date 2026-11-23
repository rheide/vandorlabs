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
    public static double x(double value) { EntityGroundVehicle c=mounted(); return c==null||c.cruise.projected()?value:value+(c.cruise.view(true,Minecraft.getMinecraft().getRenderPartialTicks())-c.posX); }
    public static double y(double value) { EntityGroundVehicle c=mounted(); return c==null?value:value+(c.flightAltitude()-c.posY); }
    public static double z(double value) { EntityGroundVehicle c=mounted(); return c==null||c.cruise.projected()?value:value+(c.cruise.view(false,Minecraft.getMinecraft().getRenderPartialTicks())-c.posZ); }
    public static double x(double value,EntityPlayer subject) { return subject==Minecraft.getMinecraft().player ? x(value) : value; }
    public static double y(double value,EntityPlayer subject) { return subject==Minecraft.getMinecraft().player ? y(value) : value; }
    public static double z(double value,EntityPlayer subject) { return subject==Minecraft.getMinecraft().player ? z(value) : value; }
    /** JourneyMap clamps distant beacons to the view radius before subtracting the render origin.
     * Both operations must start from the same interpolated camera position. */
    public static Vec3d beaconOrigin(Vec3d tickPosition) {
        if(mounted()==null)return tickPosition;
        net.minecraft.client.renderer.entity.RenderManager camera=Minecraft.getMinecraft().getRenderManager();
        return new Vec3d(x(camera.viewerPosX),y(camera.viewerPosY),z(camera.viewerPosZ));
    }
    /** JourneyMap tests the head yaw for auto-hide, while the cockpit rotates the camera with the airframe. */
    public static float beaconYaw(float headYaw) {
        Minecraft mc=Minecraft.getMinecraft();
        EntityGroundVehicle craft=mounted();
        if(craft==null||mc.gameSettings.debugCamEnable)return headYaw;
        float partial=mc.getRenderPartialTicks();
        Vec3d look=mc.player.getLook(partial);
        double[] world=craft.pilotView(craft.visualPose(partial)).vector(look.x,look.y,look.z);
        float heading=(float)Math.toDegrees(Math.atan2(-world[0],world[2]));
        return mc.gameSettings.thirdPersonView==2?heading+180:heading;
    }
    public static double beaconAngleDifference(double delta) {
        return Math.abs(mounted()==null?delta:net.minecraft.util.math.MathHelper.wrapDegrees(delta));
    }
    public static Vec3d viewPosition(Vec3d value) {
        EntityGroundVehicle c=mounted();
        return c==null?value:value.addVector(c.cruise.projected()?0:c.cruise.view(true,Minecraft.getMinecraft().getRenderPartialTicks())-c.posX,c.flightAltitude()-c.posY,
                c.cruise.projected()?0:c.cruise.view(false,Minecraft.getMinecraft().getRenderPartialTicks())-c.posZ);
    }
    private CruiseMapView(){}
}
