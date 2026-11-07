package com.vandorlabs.neoforge.client;

import com.vandorlabs.animation.PlatformMotionSmoothing;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.neoforge.mixin.NativeRampRenderPositionAccess;
import com.vandorlabs.neoforge.ramp.NativeRampMotionPayload;
import com.vandorlabs.neoforge.ramp.NativeRampRiders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Source render-only support interpolation; authoritative position/bounds remain outside the scope. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeRampRiderClient {
    private static final VisualClock CLOCK=new VisualClock();
    private static final PlatformMotionSmoothing MOTION=new PlatformMotionSmoothing();
    private static LocalPlayer owner,rendered;
    private static ClientLevel world,renderWorld;
    private static Vec3 physicalPosition,visualPosition;
    private static double xo,yo,zo,xOld,yOld,zOld,offsetX,offsetY,offsetZ,acceptedAt;
    static {NativeRampRiders.bindClientThread(()->Minecraft.getInstance()!=null&&Minecraft.getInstance().isSameThread());}
    private NativeRampRiderClient(){}
    private static double time(){var mc=Minecraft.getInstance();return CLOCK.sample(System.nanoTime(),mc.isPaused());}
    private static boolean available(LocalPlayer player){return player!=null&&player.isAlive()&&NativeRampRiders.available(player)&&player.getDeltaMovement().y<=.1;}
    private static void domain(){var mc=Minecraft.getInstance();if(owner!=mc.player||world!=mc.level){restore();MOTION.clear();offsetX=offsetY=offsetZ=0;owner=mc.player;world=mc.level;}}
    public static boolean receive(NativeRampMotionPayload message){
        var mc=Minecraft.getInstance();if(!mc.isSameThread())throw new IllegalStateException("Ramp rider correction requires client thread");
        restore();domain();var player=owner;if(player==null||world==null||player.level()!=world)return false;
        double x=player.getX(),y=player.getY(),z=player.getZ();
        if(!message.apply(player))return false;
        double dx=player.getX()-x,dy=player.getY()-y,dz=player.getZ()-z;
        player.xo+=dx;player.yo+=dy;player.zo+=dz;player.xOld+=dx;player.yOld+=dy;player.zOld+=dz;
        acceptedAt=time();MOTION.accept(dx,message.verticalDisplacement(),dz,acceptedAt);return true;
    }
    static void begin(double now){
        restore();domain();var player=owner;
        if(world==null||player.level()!=world||!available(player)){MOTION.clear();offsetX=offsetY=offsetZ=0;return;}
        offsetX=MOTION.x(now);offsetY=MOTION.y(now);offsetZ=MOTION.z(now);
        if(offsetX==0&&offsetY==0&&offsetZ==0)return;
        rendered=player;renderWorld=world;physicalPosition=player.position();
        xo=player.xo;yo=player.yo;zo=player.zo;xOld=player.xOld;yOld=player.yOld;zOld=player.zOld;
        visualPosition=new Vec3(physicalPosition.x+offsetX,physicalPosition.y+offsetY,physicalPosition.z+offsetZ);
        ((NativeRampRenderPositionAccess)(Object)player).vandorlabs$renderPosition(visualPosition);
        player.xo+=offsetX;player.yo+=offsetY;player.zo+=offsetZ;player.xOld+=offsetX;player.yOld+=offsetY;player.zOld+=offsetZ;
    }
    static void restore(){
        if(rendered==null)return;
        // Never replace a new physical teleport/world change installed by another callback.
        if(rendered.level()==renderWorld&&rendered.position()==visualPosition){
            ((NativeRampRenderPositionAccess)(Object)rendered).vandorlabs$renderPosition(physicalPosition);
            rendered.xo=xo;rendered.yo=yo;rendered.zo=zo;rendered.xOld=xOld;rendered.yOld=yOld;rendered.zOld=zOld;
        }else MOTION.clear();
        rendered=null;renderWorld=null;physicalPosition=visualPosition=null;
    }
    public static void clear(){restore();MOTION.clear();owner=null;world=null;offsetX=offsetY=offsetZ=0;}
    static double acceptedAt(){return acceptedAt;}
    static double offsetX(){return offsetX;}
    static double offsetY(){return offsetY;}
    static double offsetZ(){return offsetZ;}
    static boolean rendering(){return rendered!=null;}
    @SubscribeEvent(priority=EventPriority.HIGH)public static void pre(RenderFrameEvent.Pre event){begin(time());}
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void post(RenderFrameEvent.Post event){restore();}
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void tick(ClientTickEvent.Pre event){restore();domain();if(world==null||owner==null||owner.level()!=world||!available(owner)){MOTION.clear();offsetX=offsetY=offsetZ=0;}}
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel()==world)clear();}
}
