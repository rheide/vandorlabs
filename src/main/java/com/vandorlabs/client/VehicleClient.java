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
            if(p.type==VehicleNetwork.INSPECTION){if(mc.currentScreen instanceof GuiRedstoneChannel)((GuiRedstoneChannel)mc.currentScreen).inspection(p.origin,p.text);return;}
            if(p.type==VehicleNetwork.ERROR){
                String summary=p.text;int sentence=summary.indexOf(". ");
                if(sentence>=0)summary=summary.substring(0,sentence+1);
                if(summary.length()>110)summary=summary.substring(0,107)+"...";
                net.minecraft.util.text.TextComponentString message=new net.minecraft.util.text.TextComponentString(summary);
                message.getStyle().setHoverEvent(new net.minecraft.util.text.event.HoverEvent(net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT,new net.minecraft.util.text.TextComponentString(p.text)));
                mc.player.sendMessage(message);return;
            }
            if(p.type==VehicleNetwork.HEIGHT){if(preview!=null && preview.data.header.token.equals(p.token)){net.minecraft.nbt.NBTTagCompound tag=preview.data.structure.at(preview.data.structure.seat).tileData();tag.setInteger("PilotHeight",p.strafe);preview.data.structure=VehicleSeatSettings.replace(preview.data.structure,tag,BlockPos.ORIGIN); }return;}
            if(p.type==VehicleNetwork.SETTINGS){
                Entity raw=mc.world.getEntityByID(p.entity);
                com.vandorlabs.redstone.ChannelList channels=com.vandorlabs.redstone.ChannelList.parse(p.text);
                if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && channels!=null) {
                    EntityGroundVehicle craft=(EntityGroundVehicle)raw;
                    if(craft.structure!=null && craft.view.getTileEntity(craft.structure.seat) instanceof com.vandorlabs.tiles.TileEntityPilotSeat) {
                        com.vandorlabs.tiles.TileEntityPilotSeat seat=(com.vandorlabs.tiles.TileEntityPilotSeat)craft.view.getTileEntity(craft.structure.seat);
                        seat.setVehicleChannels(channels,mc.world);seat.configure(p.forward,p.strafe,mc.world);if(p.sequence==1)seat.setTaxiMode(p.flag);
                        craft.updateSettings(VehicleSeatSettings.replace(craft.structure,seat.writeToNBT(new net.minecraft.nbt.NBTTagCompound()),BlockPos.ORIGIN));
                    }
                }
                return;
            }
            if(p.type==VehicleNetwork.TAXI){Entity raw=mc.world.getEntityByID(p.entity);if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && ((EntityGroundVehicle)raw).structure!=null)VehicleTaxiMode.apply((EntityGroundVehicle)raw,p.flag);return;}
            if(p.type==VehicleNetwork.TILE){Entity raw=mc.world.getEntityByID(p.entity);if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && ((EntityGroundVehicle)raw).view!=null)VehicleSignals.apply((EntityGroundVehicle)raw,p);return;}
            if(p.type==VehicleNetwork.CANOPIES){Entity raw=mc.world.getEntityByID(p.entity);if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && ((EntityGroundVehicle)raw).structure!=null)VehicleCanopies.apply((EntityGroundVehicle)raw,p.data);return;}
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
        Minecraft mc=Minecraft.getMinecraft();if(preview==null || mc.currentScreen!=preview)return;
        Entity camera=mc.getRenderViewEntity();if(camera==null)return;
        double x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*event.getPartialTicks(),y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*event.getPartialTicks(),z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*event.getPartialTicks();
        GlStateManager.pushMatrix();GlStateManager.translate(-x,-y,-z);GlStateManager.disableTexture2D();GlStateManager.disableDepth();GlStateManager.depthMask(false);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.glLineWidth(1);
        for(VehicleStructure.Cell cell:preview.data.structure.cells)RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(preview.data.header.origin.add(cell.pos)).grow(.002),.2F,1F,.7F,.35F);
        GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.popMatrix();
    }
    private static final class Preview extends GuiScreen {
        final Incoming data;private GuiTextField channels;private int pilotMode,pilotHeight;private String channelsDraft;private boolean taxiDraft;
        Preview(Incoming data){this.data=data;net.minecraft.nbt.NBTTagCompound seat=data.structure.at(data.structure.seat).tileData();pilotMode=seat!=null && seat.hasKey("PilotRedstoneMode",3)?seat.getInteger("PilotRedstoneMode"):1;pilotHeight=seat==null?0:seat.getInteger("PilotHeight");taxiDraft=VehicleTaxiMode.enabled(data.structure);if(Minecraft.getMinecraft().currentScreen instanceof GuiRedstoneChannel){GuiRedstoneChannel previous=(GuiRedstoneChannel)Minecraft.getMinecraft().currentScreen;taxiDraft=previous.pilotTaxiDraft();channelsDraft=previous.pilotChannelsDraft();pilotMode=previous.pilotModeDraft();pilotHeight=previous.pilotHeightDraft();}}
        @Override public void initGui(){
            channels=new GuiTextField(0,fontRenderer,width/2-145,height-153,210,20);ChannelFields.configure(channels);
            net.minecraft.nbt.NBTTagCompound seat=data.structure.at(data.structure.seat).tileData();channels.setText(channelsDraft!=null?channelsDraft:seat==null?"0":com.vandorlabs.redstone.ChannelData.read(seat,0).toString());
            buttonList.add(new GuiButton(0,width/2-145,height-29,140,20,data.header.flag?"Park as blocks":"Assemble"));
            buttonList.add(new GuiButton(1,width/2+5,height-29,140,20,"Close"));
            buttonList.add(new GuiButton(2,width/2-145,height-77,290,20,"Taxi Mode: "+(taxiDraft?"ON":"OFF")));
            buttonList.add(new GuiButton(3,width/2+75,height-153,70,20,"Save"));
            buttonList.add(new GuiButton(4,width/2-145,height-127,140,20,modeLabel()));
            buttonList.add(new GuiButton(5,width/2+5,height-127,140,20,heightLabel()));
        }
        private String modeLabel(){return new String[]{"Trigger: Disabled","Trigger: Redstone ON","Trigger: Redstone OFF"}[pilotMode];}
        private String heightLabel(){return "Seat height: "+(pilotHeight==0?"Base":"+"+(pilotHeight*2)+" px");}
        @Override public boolean doesGuiPauseGame(){return false;}
        @Override public void updateScreen(){channels.updateCursorCounter();for(GuiButton b:buttonList)if(b.id==0 || b.id==3)b.enabled=ChannelFields.parse(channels)!=null;}
        @Override protected void mouseClicked(int x,int y,int button)throws java.io.IOException{super.mouseClicked(x,y,button);channels.mouseClicked(x,y,button);}
        @Override protected void keyTyped(char key,int code)throws java.io.IOException{if(!channels.textboxKeyTyped(key,code))super.keyTyped(key,code);}
        @Override public void drawScreen(int x,int y,float partial){
            drawRect(0,0,width,75,0xb0101820);drawRect(0,height-181,width,height,0xb0101820);
            VehicleStructure s=data.structure;
            drawCenteredString(fontRenderer,data.header.flag?"Park craft - cardinal orientation":"Assemble connected craft",width/2,12,0xffffff);
            drawCenteredString(fontRenderer,s.cells.size()+" blocks / "+(int)s.bounds.maxX+" x "+(int)s.bounds.maxY+" x "+(int)s.bounds.maxZ+" / "+s.gearCount+" landing gear",width/2,29,0xffffff);
            drawCenteredString(fontRenderer,"Check the highlighted cells before conversion.",width/2,47,0xffdd88);
            drawCenteredString(fontRenderer,"Pilot Seat channels (0 = none)",width/2,height-171,0xffffff);
            drawCenteredString(fontRenderer,"Height applies immediately; Save applies channels and trigger.",width/2,height-101,0xffdd88);
            drawCenteredString(fontRenderer,"W/S: drive   A/D: steer   Space: brake   Sneak: exit",width/2,height-49,0xffffff);
            super.drawScreen(x,y,partial);channels.drawTextBox();
        }
        @Override protected void actionPerformed(GuiButton button){
            if(button.id==4){pilotMode=GuiOptionCycle.next(pilotMode,3);button.displayString=modeLabel();return;}
            if(button.id==5){pilotHeight=GuiOptionCycle.next(pilotHeight,4);button.displayString=heightLabel();VehicleNetwork.Packet setting=new VehicleNetwork.Packet(VehicleNetwork.HEIGHT);setting.token=data.header.token;setting.entity=data.header.entity;setting.strafe=(byte)pilotHeight;PacketHandler.INSTANCE.sendToServer(setting);return;}
            if(button.id==3){com.vandorlabs.redstone.ChannelList list=ChannelFields.parse(channels);if(list==null)return;VehicleNetwork.Packet setting=new VehicleNetwork.Packet(VehicleNetwork.SETTINGS);setting.token=data.header.token;setting.entity=data.header.entity;setting.origin=data.structure.seat;setting.sequence=1;setting.flag=taxiDraft;setting.text=list.toString();setting.forward=(byte)pilotMode;setting.strafe=(byte)pilotHeight;PacketHandler.INSTANCE.sendToServer(setting);for(GuiButton b:buttonList)b.enabled=false;return;}
            if(button.id==2){VehicleNetwork.Packet setting=new VehicleNetwork.Packet(VehicleNetwork.TAXI);setting.token=data.header.token;setting.flag=!taxiDraft;PacketHandler.INSTANCE.sendToServer(setting);for(GuiButton b:buttonList)b.enabled=false;return;}
            if(button.id==0){VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.CONFIRM);packet.token=data.header.token;PacketHandler.INSTANCE.sendToServer(packet);}mc.displayGuiScreen(null);
        }
        @Override public void onGuiClosed(){if(preview==this)preview=null;}
    }
}
