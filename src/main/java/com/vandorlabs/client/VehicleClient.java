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
    private static boolean sendInteraction() {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.currentScreen!=null || mc.player==null || mc.world==null || mc.player.isSpectator())return false;
        VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);
        if(!VehicleInteraction.supported(mc.player,target))return false;
        VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.INTERACT);
        packet.entity=target.craft.getEntityId();packet.token=target.craft.getUniqueID();packet.origin=target.hit.cell.pos;
        PacketHandler.INSTANCE.sendToServer(packet);return true;
    }
    /** Consume the physical click before vanilla can emit duplicate main/offhand use packets. */
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void interact(net.minecraftforge.client.event.MouseEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        if(!event.isButtonstate() || event.getButton()!=mc.gameSettings.keyBindUseItem.getKeyCode()+100
                || mc.currentScreen!=null || mc.player==null || mc.world==null || mc.player.isSpectator())return;
        if(sendInteraction())event.setCanceled(true);
    }
    /** Keyboard bindings and controller-generated clicks use Forge's ordinary use events. */
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void use(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        if(!(event instanceof net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickEmpty
                || event instanceof net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem
                || event instanceof net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock
                || event instanceof net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract
                || event instanceof net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific))return;
        if(!event.getWorld().isRemote || event.getHand()!=net.minecraft.util.EnumHand.MAIN_HAND
                || event.getEntityPlayer()!=Minecraft.getMinecraft().player)return;
        if(sendInteraction() && event.isCancelable()) {
            event.setCanceled(true);event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
        }
    }
    private static Object lastWorld;
    private static final class Incoming {
        final VehicleNetwork.Packet header;final byte[] bytes;final long created=System.currentTimeMillis();int next;VehicleStructure structure;
        Incoming(VehicleNetwork.Packet p){header=p;bytes=new byte[p.total];}
    }
    public static void receive(VehicleNetwork.Packet p) {
        Minecraft mc=Minecraft.getMinecraft();mc.addScheduledTask(()->{
            if(mc.world==null)return;
            if(p.type==VehicleNetwork.ERROR){mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(p.text));return;}
            if(p.type==VehicleNetwork.SETTINGS){
                Entity raw=mc.world.getEntityByID(p.entity);
                com.vandorlabs.redstone.ChannelList channels=com.vandorlabs.redstone.ChannelList.parse(p.text);
                if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && channels!=null) {
                    EntityGroundVehicle craft=(EntityGroundVehicle)raw;
                    if(craft.structure!=null && craft.view.getTileEntity(craft.structure.seat) instanceof com.vandorlabs.tiles.TileEntityPilotSeat) {
                        com.vandorlabs.tiles.TileEntityPilotSeat seat=(com.vandorlabs.tiles.TileEntityPilotSeat)craft.view.getTileEntity(craft.structure.seat);
                        seat.setVehicleChannels(channels,mc.world);
                        craft.updateSettings(VehicleSeatSettings.replace(craft.structure,seat.writeToNBT(new net.minecraft.nbt.NBTTagCompound()),BlockPos.ORIGIN));
                    }
                }
                return;
            }
            if(p.type==VehicleNetwork.TAXI){Entity raw=mc.world.getEntityByID(p.entity);if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && ((EntityGroundVehicle)raw).structure!=null)VehicleTaxiMode.apply((EntityGroundVehicle)raw,p.flag);return;}
            if(p.type==VehicleNetwork.DOORS){Entity raw=mc.world.getEntityByID(p.entity);if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && ((EntityGroundVehicle)raw).structure!=null)VehicleDoors.apply((EntityGroundVehicle)raw,p.data);return;}
            if(p.type==VehicleNetwork.STATE){if(Boolean.getBoolean("vandorlabs.vehicleNetworkChecksOnly"))VehicleNetworkRuntimeChecks.state(p);Entity raw=mc.world.getEntityByID(p.entity);if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && ((EntityGroundVehicle)raw).structure!=null)((EntityGroundVehicle)raw).authoritative(p);return;}
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
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST) public void rider(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        if(!(event.getEntityPlayer().getRidingEntity() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle craft=(EntityGroundVehicle)event.getEntityPlayer().getRidingEntity();
        if(craft.structure==null)return;
        float partial=event.getPartialRenderTick();
        Vec3d at=craft.seatPosition(craft.visualPose(partial),event.getEntityPlayer());
        Entity rider=event.getEntityPlayer();
        GlStateManager.pushMatrix();
        GlStateManager.translate(at.x-(rider.lastTickPosX+(rider.posX-rider.lastTickPosX)*partial),
                at.y-(rider.lastTickPosY+(rider.posY-rider.lastTickPosY)*partial),
                at.z-(rider.lastTickPosZ+(rider.posZ-rider.lastTickPosZ)*partial));
    }
    @SubscribeEvent public void rider(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
        if(event.getEntityPlayer().getRidingEntity() instanceof EntityGroundVehicle
                && ((EntityGroundVehicle)event.getEntityPlayer().getRidingEntity()).structure!=null)GlStateManager.popMatrix();
    }
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
        if(mc.player==null || !(mc.player.getRidingEntity() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle e=(EntityGroundVehicle)mc.player.getRidingEntity();if(e.structure==null)return;cameraFor(e);
        float partial=(float)event.getRenderPartialTicks();
        Vec3d at=e.seatPosition(e.visualPose(partial),mc.player);
        Vec3d delta=at.subtract(mc.player.lastTickPosX+(mc.player.posX-mc.player.lastTickPosX)*partial,
                mc.player.lastTickPosY+(mc.player.posY-mc.player.lastTickPosY)*partial,
                mc.player.lastTickPosZ+(mc.player.posZ-mc.player.lastTickPosZ)*partial);
        // CameraSetup precedes vanilla's final pitch/yaw rotations. Express the world
        // anchor correction in that camera basis, including first-person views.
        double yaw=Math.toRadians(event.getYaw()+180),pitch=Math.toRadians(event.getPitch());
        double dx=Math.cos(yaw)*delta.x+Math.sin(yaw)*delta.z;
        double dz=-Math.sin(yaw)*delta.x+Math.cos(yaw)*delta.z;
        GlStateManager.translate(-dx,-(Math.cos(pitch)*delta.y-Math.sin(pitch)*dz),
                -(Math.sin(pitch)*delta.y+Math.cos(pitch)*dz));
        if(mc.gameSettings.thirdPersonView==0)return;
        // Vanilla mouse look already orbits the pilot; extend its camera arm and clip it against terrain.
        Vec3d eye=at.addVector(0,mc.player.getEyeHeight(),0);
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
        Minecraft mc=Minecraft.getMinecraft();if(preview==null || mc.currentScreen!=preview && !(mc.currentScreen instanceof SeatSettings))return;
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
        @Override public void initGui(){buttonList.add(new GuiButton(0,width/2-105,height-45,100,20,data.header.flag?"Park as blocks":"Assemble"));buttonList.add(new GuiButton(1,width/2+5,height-45,100,20,"Cancel"));buttonList.add(new GuiButton(2,width/2-105,height-99,210,20,"Taxi Mode: "+(VehicleTaxiMode.enabled(data.structure)?"ON":"OFF")));buttonList.add(new GuiButton(3,width/2-105,height-153,210,20,"Pilot Seat settings..."));}
        @Override public boolean doesGuiPauseGame(){return false;}
        @Override public void drawScreen(int x,int y,float partial) {
            drawRect(0,0,width,75,0xb0101820);drawRect(0,height-160,width,height,0xb0101820);
            VehicleStructure s=data.structure;
            drawCenteredString(fontRenderer,data.header.flag?"Park craft - cardinal orientation":"Assemble connected craft",width/2,12,0xffffff);
            drawCenteredString(fontRenderer,s.cells.size()+" blocks / "+(int)s.bounds.maxX+" x "+(int)s.bounds.maxY+" x "+(int)s.bounds.maxZ+" / "+s.gearCount+" landing gear",width/2,29,0xffffff);
            drawCenteredString(fontRenderer,"Check the highlighted cells. Attached hangar blocks are included.",width/2,47,0xffdd88);
            drawCenteredString(fontRenderer,VehicleTaxiMode.enabled(s)?"Sit to assemble; exit to restore blocks.":"Use the Configurizer to assemble and park.",width/2,height-119,0xffffff);
            drawCenteredString(fontRenderer,"W/S: drive   A/D: steer   Space: brake   Sneak: dismount",width/2,height-68,0xffffff);
            super.drawScreen(x,y,partial);
        }
        @Override protected void actionPerformed(GuiButton button){if(button.id==3){mc.displayGuiScreen(new SeatSettings(this));preview=this;return;}if(button.id==2){VehicleNetwork.Packet setting=new VehicleNetwork.Packet(VehicleNetwork.TAXI);setting.token=data.header.token;setting.flag=!VehicleTaxiMode.enabled(data.structure);PacketHandler.INSTANCE.sendToServer(setting);for(GuiButton control:buttonList)control.enabled=false;return;}if(button.id==0){VehicleNetwork.Packet p=new VehicleNetwork.Packet(VehicleNetwork.CONFIRM);p.token=data.header.token;PacketHandler.INSTANCE.sendToServer(p);}mc.displayGuiScreen(null);}
        @Override public void onGuiClosed(){if(preview==this)preview=null;}
    }
    private static final class SeatSettings extends GuiScreen {
        final Preview parent;private GuiTextField channels;
        SeatSettings(Preview parent){this.parent=parent;}
        @Override public boolean doesGuiPauseGame(){return false;}
        @Override public void initGui(){
            channels=new GuiTextField(0,fontRenderer,width/2-130,height/2-30,260,20);ChannelFields.configure(channels);
            net.minecraft.nbt.NBTTagCompound data=parent.data.structure.at(parent.data.structure.seat).tileData();
            channels.setText(data==null?"0":com.vandorlabs.redstone.ChannelData.read(data,0).toString());channels.setFocused(true);
            buttonList.add(new GuiButton(0,width/2-105,height/2+45,100,20,"Save"));
            buttonList.add(new GuiButton(1,width/2+5,height/2+45,100,20,"Cancel"));
        }
        @Override public void updateScreen(){channels.updateCursorCounter();buttonList.get(0).enabled=ChannelFields.parse(channels)!=null;}
        @Override public void drawScreen(int x,int y,float partial){
            drawDefaultBackground();drawCenteredString(fontRenderer,"Pilot Seat channels (0 = none)",width/2,height/2-55,0xffffff);
            channels.drawTextBox();drawCenteredString(fontRenderer,"A fresh rising pulse toggles block / vehicle mode.",width/2,height/2,0xffffff);
            drawCenteredString(fontRenderer,"Saving channels does not convert the craft.",width/2,height/2+16,0xffdd88);super.drawScreen(x,y,partial);
        }
        @Override protected void mouseClicked(int x,int y,int button)throws java.io.IOException{super.mouseClicked(x,y,button);channels.mouseClicked(x,y,button);}
        @Override protected void keyTyped(char key,int code)throws java.io.IOException{if(code==1){actionPerformed(buttonList.get(1));return;}if(!channels.textboxKeyTyped(key,code))super.keyTyped(key,code);}
        @Override protected void actionPerformed(GuiButton button){
            if(button.id==1){mc.displayGuiScreen(parent);preview=parent;return;}
            com.vandorlabs.redstone.ChannelList list=ChannelFields.parse(channels);if(list==null)return;
            VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.SETTINGS);packet.token=parent.data.header.token;
            packet.entity=parent.data.header.entity;packet.origin=parent.data.structure.seat;packet.text=list.toString();PacketHandler.INSTANCE.sendToServer(packet);
            for(GuiButton control:buttonList)control.enabled=false;
        }
    }

}
