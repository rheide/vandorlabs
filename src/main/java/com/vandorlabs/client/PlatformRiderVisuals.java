package com.vandorlabs.client;

import com.vandorlabs.animation.*;
import com.vandorlabs.network.MessagePlatformMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.*;

/** Shared camera smoothing for ramp, belt and arm support; physics remains authoritative. */
@SideOnly(Side.CLIENT)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class PlatformRiderVisuals {
    private static final VisualClock CLOCK=new VisualClock();
    private static final PlatformMotionSmoothing MOTION=new PlatformMotionSmoothing();
    private static EntityPlayer owner,rendered;
    private static double ox,oy,oz;
    private static final double[] SAVED=new double[9];
    private static double time(){return CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused());}
    public static void receive(MessagePlatformMotion message){
        EntityPlayer player=Minecraft.getMinecraft().player;if(player!=owner){MOTION.clear();owner=player;}if(player==null)return;
        double x=player.posX,y=player.posY,z=player.posZ;
        if(message.apply(player))MOTION.accept(player.posX-x,player.posY-y,player.posZ-z,time());
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){
        if(event.phase==TickEvent.Phase.END){if(rendered!=null){rendered.posX=SAVED[0];rendered.posY=SAVED[1];rendered.posZ=SAVED[2];rendered.prevPosX=SAVED[3];rendered.prevPosY=SAVED[4];rendered.prevPosZ=SAVED[5];rendered.lastTickPosX=SAVED[6];rendered.lastTickPosY=SAVED[7];rendered.lastTickPosZ=SAVED[8];rendered=null;}return;}
        Minecraft mc=Minecraft.getMinecraft();EntityPlayer player=mc.player;
        if(player!=owner||player==null||player.isDead||player.isRiding()||player.capabilities.isFlying||player.motionY>.1){MOTION.clear();owner=player;return;}
        double now=time();ox=MOTION.x(now);oy=MOTION.y(now);oz=MOTION.z(now);rendered=player;SAVED[0]=player.posX;SAVED[1]=player.posY;SAVED[2]=player.posZ;SAVED[3]=player.prevPosX;SAVED[4]=player.prevPosY;SAVED[5]=player.prevPosZ;SAVED[6]=player.lastTickPosX;SAVED[7]=player.lastTickPosY;SAVED[8]=player.lastTickPosZ;offset(player,ox,oy,oz);
    }
    private static void offset(EntityPlayer p,double x,double y,double z){
        // Render-only coordinates; never move the collision box or feed a cosmetic displacement to packets.
        p.posX+=x;p.posY+=y;p.posZ+=z;p.prevPosX+=x;p.prevPosY+=y;p.prevPosZ+=z;p.lastTickPosX+=x;p.lastTickPosY+=y;p.lastTickPosZ+=z;
    }
    public static double activeOffset(){return Math.abs(ox)+Math.abs(oy)+Math.abs(oz);}
    private PlatformRiderVisuals(){}
}
