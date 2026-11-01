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
    private static net.minecraft.util.math.BlockPos arm;private static int surface,received;
    private static final double[] SAVED=new double[9];
    private static double time(){return CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused());}
    public static void receive(MessagePlatformMotion message){
        EntityPlayer player=Minecraft.getMinecraft().player;if(player!=owner){MOTION.clear();arm=null;owner=player;}if(player==null)return;
        double x=player.posX,y=player.posY,z=player.posZ;
        if(message.apply(player)){
            double dx=player.posX-x,dy=player.posY-y,dz=player.posZ-z;
            // The support smoother owns this transition. Shift tick history too,
            // so ordinary entity interpolation does not add a second frame of lag.
            player.prevPosX+=dx;player.prevPosY+=dy;player.prevPosZ+=dz;
            player.lastTickPosX+=dx;player.lastTickPosY+=dy;player.lastTickPosZ+=dz;
            MOTION.accept(dx,message.verticalDisplacement(),dz,time());
            arm=message.supportPosition();surface=message.supportSurface();received=player.ticksExisted;
        }
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){
        if(event.phase==TickEvent.Phase.END){if(rendered!=null){rendered.posX=SAVED[0];rendered.posY=SAVED[1];rendered.posZ=SAVED[2];rendered.prevPosX=SAVED[3];rendered.prevPosY=SAVED[4];rendered.prevPosZ=SAVED[5];rendered.lastTickPosX=SAVED[6];rendered.lastTickPosY=SAVED[7];rendered.lastTickPosZ=SAVED[8];rendered=null;}return;}
        Minecraft mc=Minecraft.getMinecraft();EntityPlayer player=mc.player;
        if(player!=owner||player==null||player.isDead||player.isRiding()||player.capabilities.isFlying||player.motionY>.1){MOTION.clear();arm=null;owner=player;return;}
        double now=time();ox=MOTION.x(now);oy=MOTION.y(now);oz=MOTION.z(now);armOffset(mc,player,event.renderTickTime);rendered=player;SAVED[0]=player.posX;SAVED[1]=player.posY;SAVED[2]=player.posZ;SAVED[3]=player.prevPosX;SAVED[4]=player.prevPosY;SAVED[5]=player.prevPosZ;SAVED[6]=player.lastTickPosX;SAVED[7]=player.lastTickPosY;SAVED[8]=player.lastTickPosZ;offset(player,ox,oy,oz);
    }
    private static void armOffset(Minecraft mc,EntityPlayer player,float partial){
        if(mc.world==null||arm==null||player.ticksExisted-received>20||!mc.world.isBlockLoaded(arm))return;
        net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(arm);
        if(!(raw instanceof com.vandorlabs.gear.GearTileEntity))return;
        com.vandorlabs.gear.GearTileEntity tile=(com.vandorlabs.gear.GearTileEntity)raw;
        if(!tile.isOperational()||tile.payload()==null||tile.payload().materialized())return;
        java.util.List<net.minecraft.util.math.AxisAlignedBB> physical=com.vandorlabs.gear.GearGeometry.boxes(tile,tile.progress());
        if(surface<0||surface>=physical.size())return;
        net.minecraft.util.math.AxisAlignedBB support=physical.get(surface),body=player.getEntityBoundingBox();
        if(Math.abs(body.minY-support.maxY)>.5||body.maxX<support.minX||body.minX>support.maxX||body.maxZ<support.minZ||body.minZ>support.maxZ)return;
        double progress=com.vandorlabs.gear.client.GearRenderer.renderedProgress(tile,partial);
        java.util.List<net.minecraft.util.math.AxisAlignedBB> visual=com.vandorlabs.gear.GearGeometry.boxes(tile,progress);
        if(surface>=visual.size())return;
        net.minecraft.util.math.AxisAlignedBB shown=visual.get(surface);
        ox=(shown.minX+shown.maxX-support.minX-support.maxX)/2;
        oy=shown.maxY-support.maxY;
        oz=(shown.minZ+shown.maxZ-support.minZ-support.maxZ)/2;
    }
    private static void offset(EntityPlayer p,double x,double y,double z){
        // Render-only coordinates; never move the collision box or feed a cosmetic displacement to packets.
        p.posX+=x;p.posY+=y;p.posZ+=z;p.prevPosX+=x;p.prevPosY+=y;p.prevPosZ+=z;p.lastTickPosX+=x;p.lastTickPosY+=y;p.lastTickPosZ+=z;
    }
    public static double activeOffset(){return Math.abs(ox)+Math.abs(oy)+Math.abs(oz);}
    private PlatformRiderVisuals(){}
}
