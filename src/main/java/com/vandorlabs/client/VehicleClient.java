package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** Snapshot reassembly, standard movement keys and an explicit confirmation preview. */
public final class VehicleClient {
    private static final Map<UUID,Incoming> incoming=new HashMap<>();
    private static final Map<UUID,Incoming> completed=new HashMap<>();
    private static Preview preview;
    private static int sequence,ticks;
    private static Object lastWorld;
    private static final class Incoming {
        final VehicleNetwork.Packet header;final byte[] bytes;final long created=System.currentTimeMillis();int next;VehicleStructure structure;
        Incoming(VehicleNetwork.Packet p){header=p;bytes=new byte[p.total];}
    }
    public static void receive(VehicleNetwork.Packet p) {
        Minecraft mc=Minecraft.getMinecraft();mc.addScheduledTask(()->{
            if(mc.world==null)return;
            if(p.type==VehicleNetwork.ERROR){mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(p.text));return;}
            if(p.type==VehicleNetwork.DOORS){Entity raw=mc.world.getEntityByID(p.entity);if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && ((EntityGroundVehicle)raw).structure!=null)VehicleDoors.apply((EntityGroundVehicle)raw,p.data);return;}
            if(p.type==VehicleNetwork.STATE){Entity raw=mc.world.getEntityByID(p.entity);if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && ((EntityGroundVehicle)raw).structure!=null)((EntityGroundVehicle)raw).authoritative(p);return;}
            if(p.type!=VehicleNetwork.PREVIEW && p.type!=VehicleNetwork.SNAPSHOT)return;
            if(p.total<=0 || p.total>VehicleStructure.MAX_NBT_BYTES || p.offset<0 || p.data.length==0 || p.offset>p.total-p.data.length)return;
            incoming.values().removeIf(i->System.currentTimeMillis()-i.created>10000);
            if(p.offset==0) {if(incoming.size()>=4)return;incoming.put(p.token,new Incoming(p));}
            Incoming i=incoming.get(p.token);
            if(i==null || i.next!=p.offset || i.bytes.length!=p.total || i.header.type!=p.type || i.header.entity!=p.entity){incoming.remove(p.token);return;}
            System.arraycopy(p.data,0,i.bytes,p.offset,p.data.length);i.next+=p.data.length;
            if(i.next==i.bytes.length) {
                incoming.remove(p.token);
                try {
                    i.structure=VehicleStructure.decode(i.bytes);
                    if(p.type==VehicleNetwork.PREVIEW){preview=new Preview(i);mc.displayGuiScreen(preview);}
                    else {if(completed.size()<16)completed.put(p.token,i);apply(mc);}
                }catch(Exception e){com.vandorlabs.VandorLabs.logger.error("Rejected vehicle snapshot",e);}
            }
        });
    }
    private static void apply(Minecraft mc) {
        Iterator<Incoming> iterator=completed.values().iterator();
        while(iterator.hasNext()) {
            Incoming i=iterator.next();Entity e=mc.world.getEntityByID(i.header.entity);
            if(e instanceof EntityGroundVehicle && e.getUniqueID().equals(i.header.token)){((EntityGroundVehicle)e).install(i.structure);iterator.remove();}
            else if(System.currentTimeMillis()-i.created>10000)iterator.remove();
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.START)return;Minecraft mc=Minecraft.getMinecraft();
        RenderGroundVehicle.cleanup(mc.world);
        if(mc.world!=lastWorld){incoming.clear();completed.clear();preview=null;lastWorld=mc.world;sequence=0;}
        if(mc.world==null || mc.player==null)return;apply(mc);
        for(Entity raw:mc.world.loadedEntityList)if(raw instanceof EntityGroundVehicle)((EntityGroundVehicle)raw).setLocalDriver(mc.player.getRidingEntity()==raw);
        if(!(mc.player.getRidingEntity() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle entity=(EntityGroundVehicle)mc.player.getRidingEntity();
        VehicleNetwork.Packet p=new VehicleNetwork.Packet(VehicleNetwork.INPUT);p.entity=entity.getEntityId();p.token=entity.getUniqueID();p.sequence=++sequence;
        if(mc.currentScreen==null){p.forward=(byte)((mc.gameSettings.keyBindForward.isKeyDown()?1:0)-(mc.gameSettings.keyBindBack.isKeyDown()?1:0));p.strafe=(byte)((mc.gameSettings.keyBindRight.isKeyDown()?1:0)-(mc.gameSettings.keyBindLeft.isKeyDown()?1:0));p.flag=mc.gameSettings.keyBindJump.isKeyDown();}
        else p.flag=true;
        entity.predict(p.sequence,p.forward,p.strafe,p.flag);
        PacketHandler.INSTANCE.sendToServer(p);
    }
    private static UUID cameraCraft;
    private static double cameraDistance=4;
    public static double cameraDistance(){return cameraDistance;}
    private static void cameraFor(EntityGroundVehicle e) {
        if(!e.getUniqueID().equals(cameraCraft)){cameraCraft=e.getUniqueID();cameraDistance=Math.min(96,Math.max(6,Math.hypot(e.structure.bounds.maxX,e.structure.bounds.maxZ)*.8+4));}
    }
    @SubscribeEvent public void wheel(net.minecraftforge.client.event.MouseEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        if(event.getDwheel()==0 || mc.currentScreen!=null || mc.player==null || mc.gameSettings.thirdPersonView==0 || !(mc.player.getRidingEntity() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle e=(EntityGroundVehicle)mc.player.getRidingEntity();if(e.structure==null)return;cameraFor(e);
        cameraDistance=MathHelper.clamp(cameraDistance+(event.getDwheel()<0?2:-2),4,96);event.setCanceled(true);
    }
    @SubscribeEvent public void camera(net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup event) {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null || mc.gameSettings.thirdPersonView==0 || !(mc.player.getRidingEntity() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle e=(EntityGroundVehicle)mc.player.getRidingEntity();if(e.structure==null)return;cameraFor(e);
        // Vanilla mouse look already orbits the pilot; extend its camera arm and clip it against terrain.
        Vec3d eye=mc.player.getPositionEyes((float)event.getRenderPartialTicks());
        Vec3d away=mc.player.getLook((float)event.getRenderPartialTicks()).scale(mc.gameSettings.thirdPersonView==2?1:-1);
        double distance=cameraDistance,vanilla=4;
        for(int n=0;n<8;n++) {
            Vec3d start=eye.addVector((n%2==0?-.1:.1),(n/2%2==0?-.1:.1),(n/4==0?-.1:.1));
            RayTraceResult hit=mc.world.rayTraceBlocks(start,start.add(away.scale(cameraDistance)),false,true,false);
            if(hit!=null){double d=Math.max(0,start.distanceTo(hit.hitVec)-.1);distance=Math.min(distance,d);vanilla=Math.min(vanilla,d);}
        }
        GlStateManager.translate(0,0,-Math.max(0,distance-vanilla));
    }
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.START)RenderGroundVehicle.beginFrame();}
    @SubscribeEvent public void outline(RenderWorldLastEvent event) {
        RenderGroundVehicle.renderMissing(event.getPartialTicks());
        Minecraft mc=Minecraft.getMinecraft();if(preview==null || mc.currentScreen!=preview)return;
        Entity camera=mc.getRenderViewEntity();if(camera==null)return;
        double x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*event.getPartialTicks(),y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*event.getPartialTicks(),z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*event.getPartialTicks();
        GlStateManager.pushMatrix();GlStateManager.translate(-x,-y,-z);GlStateManager.disableTexture2D();GlStateManager.disableDepth();GlStateManager.depthMask(false);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.glLineWidth(1);
        for(VehicleStructure.Cell cell:preview.data.structure.cells)RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(preview.data.header.origin.add(cell.pos)).grow(.002),.2F,1F,.7F,.35F);
        GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.popMatrix();
    }
    private static final class Preview extends GuiScreen {
        final Incoming data;
        Preview(Incoming data){this.data=data;}
        @Override public void initGui(){buttonList.add(new GuiButton(0,width/2-105,height-45,100,20,data.header.flag?"Park as blocks":"Assemble"));buttonList.add(new GuiButton(1,width/2+5,height-45,100,20,"Cancel"));}
        @Override public boolean doesGuiPauseGame(){return false;}
        @Override public void drawScreen(int x,int y,float partial) {
            drawRect(0,0,width,75,0xb0101820);drawRect(0,height-78,width,height,0xb0101820);
            VehicleStructure s=data.structure;
            drawCenteredString(fontRenderer,data.header.flag?"Park craft - original orientation":"Assemble connected craft",width/2,12,0xffffff);
            drawCenteredString(fontRenderer,s.cells.size()+" blocks / "+(int)s.bounds.maxX+" x "+(int)s.bounds.maxY+" x "+(int)s.bounds.maxZ+" / "+s.gearCount+" landing gear",width/2,29,0xffffff);
            drawCenteredString(fontRenderer,"Check the highlighted cells. Attached hangar blocks are included.",width/2,47,0xffdd88);
            drawCenteredString(fontRenderer,"W/S: drive   A/D: steer   Space: brake   Sneak: dismount",width/2,height-68,0xffffff);
            super.drawScreen(x,y,partial);
        }
        @Override protected void actionPerformed(GuiButton button){if(button.id==0){VehicleNetwork.Packet p=new VehicleNetwork.Packet(VehicleNetwork.CONFIRM);p.token=data.header.token;PacketHandler.INSTANCE.sendToServer(p);}mc.displayGuiScreen(null);}
        @Override public void onGuiClosed(){if(preview==this)preview=null;}
    }
}
