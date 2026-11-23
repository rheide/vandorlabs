package com.vandorlabs.client;

import com.vandorlabs.vehicle.EntityGroundVehicle;
import java.util.Arrays;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/** Compare complete GPU-finished frames with the seed pass enabled and bypassed at one native terrain pose. */
@SideOnly(Side.CLIENT)
public final class CruiseTransitionProfile {
    public static final boolean ENABLED=Boolean.getBoolean("vandorlabs.cruiseTransitionProfile");
    private static CruiseTransitionProfile active;
    private final EntityGroundVehicle craft;
    private final double x,y,z;
    private final long[][] frameTimes=new long[2][64], drawTimes=new long[2][64];
    private final int[] samples=new int[2];
    private int frames;
    private long start,seedStart,drawNanos;
    private volatile boolean done;

    private CruiseTransitionProfile(EntityGroundVehicle craft){
        this.craft=craft;x=craft.posX;y=craft.posY;z=craft.posZ;
    }
    public static boolean atPose(EntityGroundVehicle craft){
        if(active==null){active=new CruiseTransitionProfile(craft);MinecraftForge.EVENT_BUS.register(active);}
        return active.done;
    }
    public static boolean startSeed(){
        if(active==null||active.done)return true;
        if(active.lane()==0)return false;
        GL11.glFinish();
        active.seedStart=System.nanoTime();
        return true;
    }
    public static void endSeed(){
        if(active!=null&&!active.done){
            GL11.glFinish();
            active.drawNanos+=System.nanoTime()-active.seedStart;
        }
    }
    // Alternating four-frame groups limit drift from chunk uploads and simulation ticks.
    private int lane(){return (frames/4)&1;}
    @SubscribeEvent public void server(TickEvent.ServerTickEvent event){
        if(event.phase==TickEvent.Phase.END&&!done)craft.setPosition(x,y,z);
    }
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){
        if(done)return;
        if(event.phase==TickEvent.Phase.START){
            GL11.glFinish();start=System.nanoTime();drawNanos=0;return;
        }
        if(start==0)return;
        GL11.glFinish();
        int mode=lane();
        if(frames>=48){
            int n=samples[mode]++;
            frameTimes[mode][n]=System.nanoTime()-start;
            drawTimes[mode][n]=drawNanos;
        }
        if(++frames<176)return;
        for(int i=0;i<2;i++){
            Arrays.sort(frameTimes[i]);Arrays.sort(drawTimes[i]);
            System.out.printf(java.util.Locale.ROOT,"[vandorlabs] transition-profile seed=%s frames=%d frame-ms p50=%.2f p95=%.2f seed-ms p50=%.2f p95=%.2f%n",
                    i!=0,samples[i],frameTimes[i][32]/1e6,frameTimes[i][60]/1e6,drawTimes[i][32]/1e6,drawTimes[i][60]/1e6);
        }
        done=true;
        MinecraftForge.EVENT_BUS.unregister(this);
    }
}
