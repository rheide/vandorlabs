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
    private static int sequence,ticks,lastJump=-100;
    private static boolean jumpDown;
    @SubscribeEvent public void hoverSneak(net.minecraftforge.client.event.InputUpdateEvent event){
        Minecraft mc=Minecraft.getMinecraft();
        if(event.getEntityPlayer()==mc.player && mc.player.getRidingEntity() instanceof EntityGroundVehicle
                && ((EntityGroundVehicle)mc.player.getRidingEntity()).hover && !mc.gameSettings.keyBindSprint.isKeyDown())
            event.getMovementInput().sneak=false;
    }
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
        final VehicleNetwork.Packet header;final byte[] bytes;final long created=System.currentTimeMillis();int next;VehicleStructure structure;net.minecraft.nbt.NBTTagCompound controlData;
        Incoming(VehicleNetwork.Packet p){header=p;bytes=new byte[p.total];}
    }
    public static void receive(VehicleNetwork.Packet p) {
        Minecraft mc=Minecraft.getMinecraft();mc.addScheduledTask(()->{
            if(mc.world==null)return;
            if(p.type==VehicleNetwork.INSPECTION){if(p.forward==1){Incoming received=new Incoming(p);if(p.data.length>0)try{net.minecraft.nbt.NBTTagCompound inspected=net.minecraft.nbt.CompressedStreamTools.readCompressed(new java.io.ByteArrayInputStream(p.data));received.controlData=inspected.getCompoundTag("Seat");received.header.token=inspected.getUniqueId("PreviewToken");}catch(java.io.IOException invalid){return;}preview=new Preview(received);mc.displayGuiScreen(preview);return;}if(mc.currentScreen instanceof GuiRedstoneChannel)((GuiRedstoneChannel)mc.currentScreen).inspection(p.origin,p.text);return;}
            if(p.type==VehicleNetwork.ERROR){
                String summary=p.text;int sentence=summary.indexOf(". ");
                if(sentence>=0)summary=summary.substring(0,sentence+1);
                if(summary.length()>110)summary=summary.substring(0,107)+"...";
                net.minecraft.util.text.TextComponentString message=new net.minecraft.util.text.TextComponentString(summary);
                message.getStyle().setHoverEvent(new net.minecraft.util.text.event.HoverEvent(net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT,new net.minecraft.util.text.TextComponentString(p.text)));
                mc.player.sendMessage(message);if(preview!=null && preview.inFlight)preview.failed(p.text);return;
            }
            if(p.type==VehicleNetwork.HEIGHT){if(preview!=null && preview.data.header.token.equals(p.token)){net.minecraft.nbt.NBTTagCompound tag=preview.data.structure.at(preview.data.structure.seat).tileData();tag.setInteger("PilotHeight",p.strafe);preview.data.structure=VehicleSeatSettings.replace(preview.data.structure,tag,BlockPos.ORIGIN); }return;}
            if(p.type==VehicleNetwork.SETTINGS){
                if(p.sequence==2){
                    if(preview!=null && preview.data.header.token.equals(p.token))try{
                        net.minecraft.nbt.NBTTagCompound ack=net.minecraft.nbt.CompressedStreamTools.readCompressed(new java.io.ByteArrayInputStream(p.data));
                        preview.data.header.token=ack.getUniqueId("PreviewToken");
                        if(preview.data.structure==null)preview.data.controlData=ack.getCompoundTag("Seat");else preview.data.structure=VehicleSeatSettings.replaceAt(preview.data.structure,p.origin,ack.getCompoundTag("Seat"),BlockPos.ORIGIN);
                        preview.inFlight=false;
                    }catch(java.io.IOException error){preview.failed("Pilot Seat acknowledgement failed; inspect again.");}
                    return;
                }

                Entity raw=mc.world.getEntityByID(p.entity);
                com.vandorlabs.redstone.ChannelList channels=com.vandorlabs.redstone.ChannelList.parse(p.text);
                if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(p.token) && channels!=null) {
                    EntityGroundVehicle craft=(EntityGroundVehicle)raw;
                    if(craft.structure!=null && craft.view.getTileEntity(p.origin) instanceof com.vandorlabs.tiles.TileEntityPilotSeat) {
                        com.vandorlabs.tiles.TileEntityPilotSeat seat=(com.vandorlabs.tiles.TileEntityPilotSeat)craft.view.getTileEntity(p.origin);
                        seat.setVehicleChannels(channels,mc.world);seat.configure(p.forward,p.strafe,mc.world);if(p.sequence==1)seat.setTaxiMode(p.flag);
                        craft.updateSettings(VehicleSeatSettings.replaceAt(craft.structure,p.origin,seat.writeToNBT(new net.minecraft.nbt.NBTTagCompound()),BlockPos.ORIGIN));
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
        PlacedSeatCamera.tick(mc);
        if(mc.world==null || mc.player==null)return;apply(mc);
        for(Entity raw:mc.world.loadedEntityList)if(raw instanceof EntityGroundVehicle)((EntityGroundVehicle)raw).setLocalDriver(mc.player.getRidingEntity()==raw && raw.getControllingPassenger()==mc.player && !((EntityGroundVehicle)raw).hover && !((EntityGroundVehicle)raw).autopilot.enabled());
        if(!(mc.player.getRidingEntity() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle entity=(EntityGroundVehicle)mc.player.getRidingEntity();
        boolean driver=entity.getControllingPassenger()==mc.player;
        VehicleNetwork.Packet p=new VehicleNetwork.Packet(VehicleNetwork.INPUT);p.entity=entity.getEntityId();p.token=entity.getUniqueID();p.sequence=++sequence;
        if(mc.currentScreen==null){p.forward=(byte)((mc.gameSettings.keyBindForward.isKeyDown()?1:0)-(mc.gameSettings.keyBindBack.isKeyDown()?1:0));p.strafe=(byte)((mc.gameSettings.keyBindRight.isKeyDown()?1:0)-(mc.gameSettings.keyBindLeft.isKeyDown()?1:0));p.flag=mc.gameSettings.keyBindJump.isKeyDown();}
        else p.flag=true;
        boolean jump=mc.currentScreen==null && mc.gameSettings.keyBindJump.isKeyDown();
        boolean toggle=jump && !jumpDown && mc.player.ticksExisted-lastJump<=7;
        if(jump && !jumpDown)lastJump=toggle?-100:mc.player.ticksExisted;
        jumpDown=jump;
        VehicleNetwork.Packet flight=new VehicleNetwork.Packet(VehicleNetwork.FLIGHT);flight.entity=p.entity;flight.token=p.token;
        flight.sequence=entity.hover && mc.currentScreen==null && mc.gameSettings.keyBindSprint.isKeyDown() && mc.gameSettings.keyBindSneak.isKeyDown()?1:0;
        flight.strafe=(byte)(driver&&mc.currentScreen==null&&mc.gameSettings.keyBindSprint.isKeyDown()?1:0);
        flight.flag=driver&&toggle;flight.forward=driver?(byte)((jump?1:0)-(mc.currentScreen==null && mc.gameSettings.keyBindSneak.isKeyDown()?1:0)):0;
        PacketHandler.INSTANCE.sendToServer(flight);
        if(driver){entity.predict(p.sequence,p.forward,p.strafe,p.flag);PacketHandler.INSTANCE.sendToServer(p);}
        else{jumpDown=false;lastJump=-100;}
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
        if(PlacedSeatCamera.wheel(event))return;
        if(event.getDwheel()==0 || mc.currentScreen!=null || mc.player==null || mc.gameSettings.thirdPersonView==0 || !(mc.player.getRidingEntity() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle e=(EntityGroundVehicle)mc.player.getRidingEntity();if(e.structure==null)return;cameraFor(e);
        cameraDistance=MathHelper.clamp(cameraDistance+(event.getDwheel()<0?2:-2),4,96);event.setCanceled(true);
    }
    @SubscribeEvent public void camera(net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup event) {
        if(PlacedSeatCamera.camera(event))return;
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
        GlStateManager.translate(0,0,(mc.gameSettings.thirdPersonView==2?1:-1)*Math.max(0,distance-vanilla));
    }
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.START)RenderGroundVehicle.beginFrame();}
    @SubscribeEvent public void outline(RenderWorldLastEvent event) {
        RenderGroundVehicle.renderMissing(event.getPartialTicks());
        Minecraft mc=Minecraft.getMinecraft();if(preview==null || mc.currentScreen!=preview || preview.data.header.sequence==-1 || preview.data.header.forward!=1 || preview.data.structure==null)return;
        Entity camera=mc.getRenderViewEntity();if(camera==null)return;
        double x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*event.getPartialTicks(),y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*event.getPartialTicks(),z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*event.getPartialTicks();
        GlStateManager.pushMatrix();GlStateManager.translate(-x,-y,-z);GlStateManager.disableTexture2D();GlStateManager.disableDepth();GlStateManager.depthMask(false);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.glLineWidth(1);
        for(VehicleStructure.Cell cell:preview.data.structure.cells)RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(preview.data.header.origin.add(cell.pos)).grow(.002),.2F,1F,.7F,.35F);
        GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.popMatrix();
    }
    private static final class Preview extends GuiScreen {
        final Incoming data;private GuiTextField channels;private int pilotMode,pilotHeight;private boolean taxiDraft,inFlight,closing;
        private final GuiTextField[] modeChannels=new GuiTextField[3];private String sentModes;private int selectedMode=-1;
        private String observed,sent,error="";private int delay;
        Preview(Incoming data){
            this.data=data;if(!hasSettings())return;
            net.minecraft.nbt.NBTTagCompound settings=settingsData();
            pilotHeight=settings.getInteger("PilotHeight");
            pilotMode=settings.hasKey("PilotRedstoneMode",3)?settings.getInteger("PilotRedstoneMode"):1;
            taxiDraft=!settings.hasKey("TaxiMode",1)||settings.getBoolean("TaxiMode");
            sent=controller()?key(com.vandorlabs.redstone.ChannelData.read(settings,0)):"";sentModes=savedModes();
        }
        private boolean controller(){return data.header.forward==1;}
        private boolean hasSettings(){return data.structure!=null||data.controlData!=null;}
        private net.minecraft.util.math.BlockPos settingsPos(){return data.structure==null?data.header.origin:settingsCell().pos;}
        private VehicleStructure.Cell settingsCell(){VehicleStructure.Cell control=data.structure.controller();return controller()&&control!=null?control:data.structure.at(data.structure.seat);}
        private net.minecraft.nbt.NBTTagCompound settingsData(){net.minecraft.nbt.NBTTagCompound tag=data.structure==null?data.controlData:settingsCell().tileData();return tag==null?new net.minecraft.nbt.NBTTagCompound():tag;}
        private String key(com.vandorlabs.redstone.ChannelList list){return list+"|"+pilotMode;}
        private int left(){return (width-256)/2;}private int top(){return (height-160)/2;}
        private int controlsTop(){return Math.max(80,height-174);}
        private int controlsLeft(){return Math.max(8,width-306);}
        private int controlsWidth(){return Math.min(292,width-24);}
        private String savedModes(){net.minecraft.nbt.NBTTagCompound n=settingsData();return com.vandorlabs.redstone.ChannelData.read(n.getCompoundTag("VehicleModeChannels0"),0)+"|"+com.vandorlabs.redstone.ChannelData.read(n.getCompoundTag("VehicleModeChannels1"),0)+"|"+com.vandorlabs.redstone.ChannelData.read(n.getCompoundTag("VehicleModeChannels2"),0);}
        private String draftKey(){String text=channels.getText();for(GuiTextField f:modeChannels)text+="|"+f.getText();return text;}
        @Override public void initGui(){
            buttonList.clear();
            if(!controller()){
                buttonList.add(new GuiButton(5,left()+14,top()+48,126,20,heightLabel()));
                buttonList.add(new GuiButton(2,left()+14,top()+76,126,20,"Taxi Mode: "+(taxiDraft?"ON":"OFF")));
                buttonList.add(new GuiButton(1,left()+14,top()+112,126,20,"Done"));return;
            }
            if(hasSettings()){
                String draft=channels==null?com.vandorlabs.redstone.ChannelData.read(settingsData(),0).toString():channels.getText();
                int x=controlsLeft(),t=controlsTop(),w=controlsWidth();channels=new GuiTextField(0,fontRenderer,x,t+17,w-139,18);ChannelFields.configure(channels);channels.setText(draft);
                buttonList.add(new GuiButton(4,x+w-132,t+16,132,20,modeLabel()));
                for(int i=0;i<3;i++){
                    String value=modeChannels[i]==null?com.vandorlabs.redstone.ChannelData.read(settingsData().getCompoundTag("VehicleModeChannels"+i),0).toString():modeChannels[i].getText();
                    modeChannels[i]=new GuiTextField(10+i,fontRenderer,x+42,t+43+i*26,w-140,18);ChannelFields.configure(modeChannels[i]);modeChannels[i].setText(value);
                    buttonList.add(new GuiButton(10+i,x+w-92,t+42+i*26,92,20,new String[]{"Park","Taxi","Hover"}[i]));
                }
                observed=draftKey();
            }
            buttonList.add(new GuiButton(1,width/2-60,height-26,120,20,"Close"));
            org.lwjgl.input.Keyboard.enableRepeatEvents(true);refreshButtons();
        }
        private String modeLabel(){return new String[]{"Signal: Disabled","Signal: ON","Signal: OFF"}[pilotMode];}
        private String heightLabel(){return "Height: "+(pilotHeight==0?"Base":"+"+(pilotHeight*2)+" px");}
        @Override public boolean doesGuiPauseGame(){return false;}
        private void refreshButtons(){for(GuiButton b:buttonList){if(b.id>=10&&b.id<=12){int mode=b.id-10;b.enabled=data.structure!=null&&mode!=data.header.strafe&&!inFlight&&(mode!=0||data.header.sequence!=-1)&&(mode!=1||!data.structure.hasNonWheeledGear);b.displayString=new String[]{"Park","Taxi","Hover"}[mode]+(mode==data.header.strafe?" (current)":"");}if(b.id==4)b.displayString=modeLabel();if(b.id==5)b.displayString=heightLabel();if(b.id==2)b.displayString="Taxi Mode: "+(taxiDraft?"ON":"OFF");}}
        private void failed(String message){inFlight=false;error=message;if(controller()&&channels!=null){com.vandorlabs.redstone.ChannelList value=ChannelFields.parse(channels);if(value!=null)sent=key(value);}else sent=pilotHeight!=settingsData().getInteger("PilotHeight")?"H|"+pilotHeight:"T|"+taxiDraft;closing=false;selectedMode=-1;sentModes=modeChannels[0]==null?savedModes():modeChannels[0].getText()+"|"+modeChannels[1].getText()+"|"+modeChannels[2].getText();}
        private void apply(boolean controls){
            if(inFlight||!hasSettings())return;
            VehicleNetwork.Packet setting;
            if(controller()){
                com.vandorlabs.redstone.ChannelList list=ChannelFields.parse(channels);if(list==null){if(!controls)return;list=com.vandorlabs.redstone.ChannelData.read(settingsData(),0);}
                String next=key(list);
                if(next.equals(sent)){
                    com.vandorlabs.redstone.ChannelList[] values=new com.vandorlabs.redstone.ChannelList[3];for(int i=0;i<3;i++){values[i]=ChannelFields.parse(modeChannels[i]);if(values[i]==null)return;}
                    String modes=values[0]+"|"+values[1]+"|"+values[2];if(modes.equals(sentModes))return;
                    inFlight=true;error="";sentModes=modes;PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessagePilotModes(settingsPos(),values,data.header.token,data.header.entity));return;
                }
                setting=new VehicleNetwork.Packet(VehicleNetwork.SETTINGS);setting.sequence=0;setting.text=list.toString();setting.forward=(byte)pilotMode;sent=next;
            }else{
                net.minecraft.nbt.NBTTagCompound saved=settingsData();
                if(pilotHeight!=saved.getInteger("PilotHeight")){
                    String next="H|"+pilotHeight;if(next.equals(sent))return;sent=next;setting=new VehicleNetwork.Packet(VehicleNetwork.HEIGHT);setting.strafe=(byte)pilotHeight;
                }else{
                    boolean savedTaxi=!saved.hasKey("TaxiMode",1)||saved.getBoolean("TaxiMode");if(taxiDraft==savedTaxi)return;
                    String next="T|"+taxiDraft;if(next.equals(sent))return;sent=next;setting=new VehicleNetwork.Packet(VehicleNetwork.TAXI);setting.flag=taxiDraft;
                }
            }
            setting.token=data.header.token;setting.entity=data.header.entity;setting.origin=settingsPos();
            inFlight=true;error="";PacketHandler.INSTANCE.sendToServer(setting);
        }
        @Override public void updateScreen(){
            if(channels!=null){channels.updateCursorCounter();for(GuiTextField f:modeChannels)f.updateCursorCounter();String current=draftKey();if(!current.equals(observed)){observed=current;delay=6;error="";}if(delay>0)delay--;else apply(false);}
            else if(!controller())apply(false);
            refreshButtons();if(closing||selectedMode>=0){apply(true);if(!inFlight){if(selectedMode>=0){VehicleNetwork.Packet p=new VehicleNetwork.Packet(VehicleNetwork.MODE_SELECT);p.token=data.header.token;p.entity=data.header.entity;p.origin=settingsPos();p.sequence=selectedMode;PacketHandler.INSTANCE.sendToServer(p);}mc.displayGuiScreen(null);}}
        }
        @Override protected void mouseClicked(int x,int y,int button)throws java.io.IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,2,4,5))return;super.mouseClicked(x,y,button);if(channels!=null){channels.mouseClicked(x,y,button);for(GuiTextField f:modeChannels)f.mouseClicked(x,y,button);}}
        @Override protected void keyTyped(char key,int code)throws java.io.IOException{if(code==1){closing=true;apply(true);return;}boolean used=channels!=null&&channels.textboxKeyTyped(key,code);if(channels!=null)for(GuiTextField f:modeChannels)used|=f.textboxKeyTyped(key,code);if(!used)super.keyTyped(key,code);}
        @Override public void drawScreen(int x,int y,float partial){
            if(controller()){
                drawRect(0,0,width,76,0xc0101820);drawRect(controlsLeft()-7,controlsTop()-5,width-7,height-30,0xe0101820);drawRect(0,height-30,width,height,0xc0101820);
                drawCenteredString(fontRenderer,"Vehicle Controller",width/2,10,0xffffff);
                VehicleStructure structure=data.structure;
                drawCenteredString(fontRenderer,structure==null?"Selection unavailable":structure.cells.size()+" blocks / "+structure.pilotSeats().size()+" Pilot Seats / "+structure.gearCount+" supports",width/2,27,0xffffff);
                String status=!error.isEmpty()?error:structure==null||data.header.sequence==-1?data.header.text:"Check the highlighted selection before conversion.";
                fontRenderer.drawSplitString(status,width/2-Math.min(300,width-20)/2,45,Math.min(300,width-20),0xffdd88);
                if(channels!=null){int l=controlsLeft(),t=controlsTop();fontRenderer.drawString("Toggle channels (0 = none)",l,t+4,0xffffff);for(int i=0;i<3;i++)fontRenderer.drawString(new String[]{"Park","Taxi","Hover"}[i],l,t+48+i*26,0xffffff);drawCenteredString(fontRenderer,inFlight?"Applying...":"Mode channels: 0 = unassigned",l+controlsWidth()/2,t+123,0xaabbcc);}
            }else{
                drawDefaultBackground();int l=left(),t=top();drawRect(l,t,l+256,t+160,0xdd171b23);drawRect(l+6,t+6,l+250,t+154,0xff343c49);
                fontRenderer.drawString("Pilot Seat",l+14,t+11,0xffffff);drawRect(l+151,t+26,l+243,t+135,0xff111820);fontRenderer.drawString("Preview",l+159,t+33,0xdddddd);
                net.minecraft.item.ItemStack model=new net.minecraft.item.ItemStack(com.vandorlabs.blocks.ModBlocks.PILOT_SEAT);net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tag.setInteger("PilotHeight",pilotHeight);model.setTagInfo("BlockEntityTag",tag);
                GlStateManager.pushMatrix();GlStateManager.translate(l+169,t+57,0);GlStateManager.scale(3.5F,3.5F,1);RenderHelper.enableGUIStandardItemLighting();itemRender.renderItemAndEffectIntoGUI(model,0,0);RenderHelper.disableStandardItemLighting();GlStateManager.popMatrix();
                if(!error.isEmpty())fontRenderer.drawSplitString(error,l+14,t+98,126,0xffdd88);
            }
            super.drawScreen(x,y,partial);if(channels!=null){channels.drawTextBox();for(GuiTextField f:modeChannels)f.drawTextBox();}
        }
        @Override protected void actionPerformed(GuiButton button){
            if(button.id>=10&&button.id<=12&&button.enabled){selectedMode=button.id-10;return;}
            if(button.id==4)pilotMode=GuiOptionCycle.next(pilotMode,3);else if(button.id==5)pilotHeight=GuiOptionCycle.next(pilotHeight,4);else if(button.id==2)taxiDraft=!taxiDraft;else if(button.id==1)closing=true;
            apply(true);refreshButtons();
        }
        @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);if(preview==this)preview=null;}
    }
}
