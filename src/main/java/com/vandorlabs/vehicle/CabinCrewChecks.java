package com.vandorlabs.vehicle;

import com.vandorlabs.client.VehicleClient;
import com.vandorlabs.config.BlockAvailability;
import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Both riders connect and send normal keyboard and interaction packets through their own Minecraft clients. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class CabinCrewChecks {
    static final BlockPos CHAIR=new BlockPos(4,1,0), SPARE=new BlockPos(3,1,3);
    private static final boolean PHYSICAL=!"anchored".equals(System.getenv("VANDOR_LABS_REENTRY_MODE"));
    private static final String PEER="CrewPeer", PREFIX="[VLCrewFixture] ";
    private static EntityGroundVehicle craft;
    private static EntityPlayerMP host,peer;
    private static int stage,ticks,actionTicks;
    private static String action="";
    private static Vec3d hostStart,peerStart;
    private static boolean jumped;
    private static double exitZ;
    private static long deadline;
    private static Future<?> pending;

    private static void need(boolean good,String message){
        if(!good)throw new IllegalStateException("Cabin crew stage="+stage+" ticks="+ticks+" "+message);
    }
    private static void next(int n){stage=n;ticks=0;}
    private static void order(String text){peer.connection.sendPacket(new SPacketChat(new TextComponentString(PREFIX+text)));}
    private static void forward(Minecraft mc,boolean held){CabinWalkChecks.key(mc.gameSettings.keyBindForward,held);}
    private static EntityGroundVehicle local(Minecraft mc){
        if(mc.player==null)return null;
        Entity e=mc.player.getRidingEntity();
        return e instanceof EntityGroundVehicle&&((EntityGroundVehicle)e).view!=null?(EntityGroundVehicle)e:null;
    }
    private static boolean seatClick(Minecraft mc,EntityGroundVehicle vehicle,BlockPos seat)throws Exception{
        Vec3d feet=vehicle.cabin.position(mc.player,1);
        if(feet==null)return false;
        Vec3d delta=new Vec3d(seat).addVector(.5,.45,.5).subtract(feet.addVector(0,mc.player.getEyeHeight(),0));
        CabinWalkChecks.look(mc,(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        VehicleInteraction.Target hit=VehicleInteraction.pointed(mc.player);
        if(hit==null||hit.hit.action!=VehicleSelection.Action.BOARD||!VehicleSeats.lower(hit.hit.cell.state,hit.hit.cell.pos).equals(seat))return false;
        CabinWalkChecks.click(mc);
        return true;
    }
    private static void setup(Minecraft mc)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;
        mc.gameSettings.thirdPersonView=1;
        mc.displayGuiScreen(null);
        Configuration cfg=new Configuration(new File(mc.mcDataDir,"config/crew-test.cfg"));
        cfg.load();cfg.get("vehicles","cruiseCoordinates","anchored").set(PHYSICAL?"physical":"anchored");cfg.save();
        BlockAvailability.load(cfg.getConfigFile());
        mc.getIntegratedServer().setOnlineMode(false);
        mc.getIntegratedServer().getNetworkSystem().addLanEndpoint(InetAddress.getLoopbackAddress(),Integer.getInteger("vandorlabs.cabinCrewPort"));
        deadline=System.nanoTime()+300_000_000_000L;
        System.out.println("[vandorlabs] cabin crew READY");
    }

    static boolean host(Minecraft mc)throws Exception{
        need(++ticks<1400&&(deadline==0||System.nanoTime()<deadline),"timeout; host="+local(mc)+" feet="+(local(mc)==null?null:local(mc).cabin.position(mc.player,1))+" action="+action);
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;}
        EntityGroundVehicle vehicle=local(mc);
        if(stage==0){setup(mc);next(1);}
        else if(stage==1){
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                peer=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(PEER);
                if(peer==null)return;
                host=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                craft=CabinWalkChecks.aircraft(host,true);
                peer.connection.setPlayerLocation(host.posX,host.posY,host.posZ,0,0);
                need(craft.board(host,CHAIR)&&craft.board(peer)&&craft.getControllingPassenger()==peer,"pilot boards second, passenger boards first");
            });
            next(2);
        }else if(stage==2&&peer==null){stage=1;}
        else if(stage==2&&vehicle!=null&&vehicle.getPassengers().size()==2&&ticks>15){
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                craft.requestFlight(peer);
                craft.setPosition(craft.posX,craft.cruise.handoffAltitude()+2,craft.posZ);
                need(craft.cruise.move(0,1,0)&&!craft.cruise.projected(),"physical configuration falls back for two connected riders");
                need(craft.cruise.move(0,64,8192),"remote route");VehicleNetwork.state(craft);
            });
            next(3);
        }else if(stage==3&&vehicle!=null&&vehicle.cruise.active()&&vehicle.flying()){
            CabinWalkChecks.chord(mc,true);next(4);
        }else if(stage==4&&vehicle!=null&&vehicle.walkingInCabin(mc.player)){
            CabinWalkChecks.chord(mc,false);
            hostStart=vehicle.cabin.position(mc.player,1);
            CabinWalkChecks.look(mc,0,0);forward(mc,true);CabinWalkChecks.key(mc.gameSettings.keyBindJump,true);
            next(5);
        }else if(stage==5&&vehicle!=null){
            Vec3d current=vehicle.cabin.position(mc.player,1);
            jumped|=current.y>hostStart.y+.3;
            if(ticks>=4)CabinWalkChecks.key(mc.gameSettings.keyBindJump,false);
            if(current.z-hostStart.z>.9)forward(mc,false);
            if(!jumped||current.z-hostStart.z<.9||Math.abs(current.y-hostStart.y)>.02||ticks<16)return false;
            if(!Boolean.FALSE.equals(VehicleClient.renderedRiderSitting(mc.player.getUniqueID()))
                    ||!Boolean.TRUE.equals(VehicleClient.renderedRiderSitting(peer.getUniqueID())))return false;
            System.out.println("[vandorlabs] cabin crew mixed render PASS standing passenger and seated pilot");
            // The real host is the passenger, so these inputs must not pitch the craft.
            need(Math.abs(vehicle.flightPitch())<.05&&Math.abs(vehicle.flightBank())<.05,"walking passenger steered seated pilot's aircraft");
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                need(craft.getControllingPassenger()==peer&&!craft.board(host,craft.structure.seat),"occupied cockpit cannot be taken");
                order("walk");
            });
            next(6);
        }else if(stage==6&&vehicle!=null){
            Entity actor=mc.world.getPlayerEntityByUUID(peer.getUniqueID());
            if(actor==null||!vehicle.walkingInCabin(actor))return false;
            if(!Boolean.FALSE.equals(VehicleClient.renderedRiderSitting(peer.getUniqueID())))return false;
            if(peerStart==null){peerStart=vehicle.cabin.position(actor,1);return false;}
            if(vehicle.cabin.position(actor,1).z-peerStart.z<.55)return false;
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                need(craft.cabin.walking(host)&&craft.cabin.walking(peer),"both connected riders standing");
                Vec3d a=craft.cabin.position(host,1),b=craft.cabin.position(peer,1);
                need(a.distanceTo(b)>.6,"independent local feet");
                EntityGroundVehicle restored=new EntityGroundVehicle(craft.world);
                NBTTagCompound saved=craft.writeToNBT(new NBTTagCompound());
                restored.readFromNBT(saved);
                need(restored.cabin.position(host,1).distanceTo(a)<1e-5&&restored.cabin.position(peer,1).distanceTo(b)<1e-5,"two persisted walkers");
                NBTTagCompound legacy=saved.getCompoundTag("FlightCabin").getTagList("Walkers",10).getCompoundTagAt(0);
                saved.setTag("FlightCabin",legacy);restored.readFromNBT(saved);
                need(restored.cabin.walking(host)!=restored.cabin.walking(peer),"original single walker NBT restores exactly one");
                restored.setDead();order("hold");
            });
            next(7);
        }else if(stage==7&&vehicle!=null){
            if(vehicle.walkingInCabin(mc.player)){if(ticks%5==0)seatClick(mc,vehicle,vehicle.structure.seat);return false;}
            need(vehicle.getControllingPassenger()==mc.player,"passenger takes unoccupied pilot seat");
            pending=mc.getIntegratedServer().addScheduledTask(()->order("spare"));next(8);
        }else if(stage==8&&vehicle!=null){
            Entity actor=mc.world.getPlayerEntityByUUID(peer.getUniqueID());
            if(actor==null||vehicle.walkingInCabin(actor)||!vehicle.seatForPassenger(actor).equals(SPARE))return false;
            if(!Boolean.TRUE.equals(VehicleClient.renderedRiderSitting(peer.getUniqueID())))return false;
            need(vehicle.getControllingPassenger()==mc.player,"peer sat in spare chair while host retains controls");
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                need(!craft.board(host,SPARE)&&craft.getControllingPassenger()==host,"occupied spare chair preserved");
                exitZ=craft.cruise.z();order("exit");
            });next(9);
        }else if(stage==9&&vehicle!=null&&vehicle.getPassengers().size()==1&&vehicle.cruise.projected()==PHYSICAL){
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                need(peer.getRidingEntity()==null&&Math.abs(peer.posZ-exitZ)<150&&peer.posY<craft.flightAltitude()+5,"walked off at geographic position");
                need(craft.getControllingPassenger()==host&&craft.poweredFlight(),"remaining pilot continues in Physical");
                order("pass");
            });next(10);
        }else if(stage==10&&ticks>30){
            System.out.println("[vandorlabs] cruise-physical PASS two connected clients walk, jump, exchange pilot/passenger seats, occupy independently, save both feet, and resume selected cruise mode after edge exit");
            return true;
        }
        return false;
    }

    @SubscribeEvent public static void command(ClientChatReceivedEvent event){
        if(!Boolean.getBoolean("vandorlabs.cabinCrewPeer"))return;
        String message=event.getMessage().getUnformattedText();
        if(message.startsWith(PREFIX)){action=message.substring(PREFIX.length());actionTicks=0;event.setCanceled(true);}
    }
    @SubscribeEvent public static void peerTick(TickEvent.ClientTickEvent event)throws Exception{
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("vandorlabs.cabinCrewPeer"))return;
        Minecraft mc=Minecraft.getMinecraft();mc.gameSettings.pauseOnLostFocus=false;
        if(action.equals("pass")){
            System.out.println("[vandorlabs] cruise-physical PASS connected crew peer packet controls, chair click, geographic edge exit");mc.shutdown();action="";return;
        }
        EntityGroundVehicle vehicle=local(mc);
        if(vehicle==null){if(action.equals("exit"))forward(mc,false);return;}
        if(mc.currentScreen!=null){mc.displayGuiScreen(null);mc.setIngameFocus();}
        boolean walking=vehicle.walkingInCabin(mc.player);
        if(action.equals("walk")||action.equals("exit")){
            CabinWalkChecks.chord(mc,!walking);
            if(walking){
                CabinWalkChecks.look(mc,action.equals("exit")?180:0,0);forward(mc,action.equals("exit")||++actionTicks<=9);
            }
        }else if(action.equals("hold")){forward(mc,false);CabinWalkChecks.chord(mc,false);}
        else if(action.equals("spare")){
            forward(mc,false);
            if(walking&&++actionTicks%5==0)seatClick(mc,vehicle,SPARE);
        }
    }
    private CabinCrewChecks(){}
}
