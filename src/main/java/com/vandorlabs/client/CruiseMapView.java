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
        float pitch=mc.player.prevRotationPitch+(mc.player.rotationPitch-mc.player.prevRotationPitch)*partial;
        float yaw=mc.player.prevRotationYaw+net.minecraft.util.math.MathHelper.wrapDegrees(mc.player.rotationYaw-mc.player.prevRotationYaw)*partial;
        Vec3d look=Vec3d.fromPitchYaw(pitch,yaw);
        double[] world=craft.pilotView(craft.visualPose(partial)).vector(look.x,look.y,look.z);
        float heading=(float)Math.toDegrees(Math.atan2(-world[0],world[2]));
        return mc.gameSettings.thirdPersonView==2?heading+180:heading;
    }
    private static final java.nio.FloatBuffer billboard=org.lwjgl.BufferUtils.createFloatBuffer(16);
    /** JourneyMap applied its scale and vanilla head rotations, but the cockpit camera can
     * face the back of that plane. Keep its eye-space position and pixel scale and use
     * screen-right and screen-down axes for both the glyphs and the marker icon. */
    public static void faceCamera() {
        if(mounted()==null)return;
        billboard.clear();
        org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX,billboard);
        for(int col=0;col<3;col++){
            int i=4*col;
            float x=billboard.get(i),y=billboard.get(i+1),z=billboard.get(i+2);
            float size=(float)Math.sqrt(x*x+y*y+z*z);
            for(int row=0;row<3;row++)billboard.put(i+row,row==col?(col==0?size:-size):0);
        }
        org.lwjgl.opengl.GL11.glLoadMatrix(billboard);
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
