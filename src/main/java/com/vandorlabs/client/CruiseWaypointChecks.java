package com.vandorlabs.client;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Observe the installed JourneyMap renderer's formatted distance for an aimed and an off-axis beacon. */
@SideOnly(Side.CLIENT)
public final class CruiseWaypointChecks {
    private static String label;
    private static boolean checked;
    private static boolean sampling, upright;
    public static void inspectLabelPlane(){
        if(!sampling)return;
        java.nio.FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);
        org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX,matrix);
        upright=matrix.get(0)>0&&matrix.get(5)<0&&Math.abs(matrix.get(1))<1e-5&&Math.abs(matrix.get(4))<1e-5;
    }
    public static void formatted(String value){label=value;}

    public static void check(Minecraft mc) throws Exception {
        if(checked||System.getenv("VANDOR_LABS_TEST_JOURNEYMAP")==null)return;
        Class<?> waypoint=Class.forName("journeymap.client.model.Waypoint");
        Class<?> type=Class.forName("journeymap.client.model.Waypoint$Type");
        Method at=waypoint.getMethod("at",BlockPos.class,type,int.class);
        Class<?> renderer=Class.forName("journeymap.client.render.ingame.RenderWaypointBeacon");
        Method render=renderer.getDeclaredMethod("doRender",waypoint);
        render.setAccessible(true);
        // Initialize the same properties and view entity used by the normal world render pass.
        renderer.getMethod("renderAll").invoke(null);
        java.lang.reflect.Field field=renderer.getDeclaredField("waypointProperties");
        field.setAccessible(true);
        Object properties=field.get(null);
        Object hide=properties.getClass().getField("autoHideLabel").get(properties);
        Object distance=properties.getClass().getField("showDistance").get(properties);
        Method get=hide.getClass().getMethod("get"),set=hide.getClass().getMethod("set",Boolean.class);
        Object oldHide=get.invoke(hide),oldDistance=get.invoke(distance);
        float old=mc.player.rotationYawHead;
        try{
            set.invoke(hide,true);set.invoke(distance,true);
            float yaw=CruiseMapView.beaconYaw(old);
            mc.player.rotationYawHead=yaw+100;
            Vec3d origin=CruiseMapView.beaconOrigin(mc.player.getPositionVector());
            for(int turn:new int[]{0,90}){
                double radians=Math.toRadians(yaw+turn);
                BlockPos target=new BlockPos(origin.x-900*Math.sin(radians),origin.y,origin.z+900*Math.cos(radians));
                Object marker=at.invoke(null,target,type.getField("Normal").get(null),0);
                label=null;upright=false;sampling=true;
                render.invoke(null,marker);
                if((turn==0)!=(label!=null))throw new IllegalStateException("JourneyMap aimed distance at yaw="+yaw+" turn="+turn+" label="+label);
                if(turn==0){
                    if(!upright)throw new IllegalStateException("JourneyMap text is mirrored or upside down");
                    System.out.println("[vandorlabs] JourneyMap aimed waypoint upright distance="+label);
                }
            }
            checked=true;
        }finally{
            sampling=false;mc.player.rotationYawHead=old;
            set.invoke(hide,oldHide);set.invoke(distance,oldDistance);
        }
    }
    private CruiseWaypointChecks(){}
}
