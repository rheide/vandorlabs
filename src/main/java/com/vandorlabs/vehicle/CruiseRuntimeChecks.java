package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.client.CruiseSky;
import com.vandorlabs.client.VehicleClient;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;

@SideOnly(Side.CLIENT)
public final class CruiseRuntimeChecks {
    private static final String RESTART = System.getenv("VANDOR_LABS_CRUISE_PHASE");
    private static final String SAVE_KEY = "VandorCruiseFixture";
    private static int stage,ticks,teleportSettled;
    private static Future<?> work;
    private static EntityGroundVehicle craft;
    private static double startX,destination;
    private static void require(boolean ok,String detail){if(!ok)throw new IllegalStateException("Cruise: "+detail);}

    @SideOnly(Side.CLIENT)
    public static final class TransitChunks {
        private int loads;

        @SubscribeEvent public void loaded(ChunkEvent.Load event) {
            if (event.getWorld()!=craft.world) return;
            int x=event.getChunk().x*16, z=event.getChunk().z*16;
            if (x>=startX+512 && x<=startX+4112 && Math.abs(z-craft.posZ)<256) loads++;
        }
    }
    private static final TransitChunks transitChunks=new TransitChunks();

    private static void observeRoute() {
        transitChunks.loads=0;
        MinecraftForge.EVENT_BUS.register(transitChunks);
    }

    private static void checkRoute() {
        MinecraftForge.EVENT_BUS.unregister(transitChunks);
        require(transitChunks.loads==0,"loaded "+transitChunks.loads+" transit chunks before descent");
    }

    private static void place(Minecraft mc){
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        WorldServer world=pilot.getServerWorld();
        BlockPos seat=BlockPos.ORIGIN;
        craft=new EntityGroundVehicle(world);
        craft.install(new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null),
                new VehicleStructure.Cell(seat.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null)),seat,EnumFacing.NORTH,1));
        startX=pilot.posX;
        craft.setPosition(startX,255.9,pilot.posZ);
        world.spawnEntity(craft);
        pilot.setPositionAndUpdate(startX,256,pilot.posZ);
        craft.assignSeat(pilot,seat);
        require(pilot.startRiding(craft,true),"pilot boards");
        craft.hover=true;craft.hoverTarget=255.9;
    }

    private static void enter(){
        // Set a recovery flight so the production physics levels the craft and descends on its own.
        NBTTagCompound state=new NBTTagCompound();state.setByte("MaxwellFlightPhase",(byte)3);
        craft.readFlightMode(state);
        craft.setPosition(startX,255.9,craft.posZ);
        observeRoute();
        require(craft.cruise.move(0,1,0),"cross build ceiling");
        for(int i=0;i<64;i++)require(craft.cruise.move(64,16,0),"route step "+i);
        destination=craft.cruise.x();
        require(Math.abs(craft.posX-startX)<.001&&destination-startX==4096,"horizontal route keeps physical anchor");
        require(craft.cruise.altitude()>1200&&Double.isInfinite(craft.groundClearance()),"logical altitude without a clearance query");
        for(int i=512;i<=4096;i+=256)require(!craft.world.isBlockLoaded(new BlockPos(startX+i,64,craft.posZ)),"cruise loaded route at "+i);
        int modes=VehicleStatus.availableModes(craft.view,BlockPos.ORIGIN,(EntityPlayerMP)craft.getControllingPassenger());
        require((modes & 6)==0 && (modes & 8)!=0,"only Flight remains selectable at high altitude");
        require(Math.abs(com.vandorlabs.navigation.NavigationVehicle.center(craft).x-destination)<3,"navigation follows geographic route");
        NBTTagCompound saved=craft.writeToNBT(new NBTTagCompound());
        EntityGroundVehicle loaded=new EntityGroundVehicle(craft.world);
        loaded.readFromNBT(saved);
        require(loaded.cruise.active()&&loaded.cruise.x()==destination&&loaded.cruise.altitude()==craft.cruise.altitude(),"persisted route and altitude");
    }

    private static void saveFlight(Minecraft mc) {
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        checkRoute();
        NBTTagCompound marker=new NBTTagCompound();
        marker.setUniqueId("Aircraft",craft.getUniqueID());
        marker.setDouble("OriginX",startX);
        marker.setDouble("DestinationX",destination);
        pilot.getEntityData().setTag(SAVE_KEY,marker);
        mc.getIntegratedServer().getPlayerList().saveAllPlayerData();
        mc.getIntegratedServer().saveAllWorlds(false);
    }

    private static void resumeFlight(Minecraft mc) {
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        NBTTagCompound marker=pilot.getEntityData().getCompoundTag(SAVE_KEY);
        require(marker.hasUniqueId("Aircraft"),"cold restart has the saved player");
        require(pilot.getRidingEntity() instanceof EntityGroundVehicle,"cold restart restores the mounted pilot");
        craft=(EntityGroundVehicle)pilot.getRidingEntity();
        require(craft.getUniqueID().equals(marker.getUniqueId("Aircraft")),"same saved aircraft");
        startX=marker.getDouble("OriginX");
        destination=marker.getDouble("DestinationX");
        require(craft.cruise.active() && craft.cruise.x()==destination && craft.posX==startX,"restored cruise route and anchor");
        require(craft.cruise.altitude()>256 && craft.descendingFromFlight(),"restored airborne recovery");
        observeRoute();
    }

    public static boolean tick(Minecraft mc,File out) throws Exception {
        if(CruisePhysicalChecks.enabled())return CruisePhysicalChecks.tick(mc,out);
        if(Boolean.getBoolean("vandorlabs.cruiseResidentChecksOnly"))return CruiseResidentChecks.tick(mc);
        if(++ticks>(stage==2?2000:600))throw new IllegalStateException("Cruise fixture timeout at stage "+stage+" rider="+mc.player.getRidingEntity()+" position="+mc.player.getPosition()+" start="+startX+" dest="+destination);
        if(work!=null){if(!work.isDone())return false;work.get();work=null;ticks=0;}
        if(stage==0 && "read".equals(RESTART)){
            work=mc.getIntegratedServer().addScheduledTask(()->resumeFlight(mc));stage=2;
        }else if(stage==0){
            work=mc.getIntegratedServer().addScheduledTask(()->place(mc));stage=1;
        }else if(stage==1&&ticks>10&&mc.player.getRidingEntity() instanceof EntityGroundVehicle){
            work=mc.getIntegratedServer().addScheduledTask(()->{
                NBTTagCompound flight=new NBTTagCompound();
                flight.setByte("MaxwellFlightPhase",(byte)3);
                craft.readFlightMode(flight);
            });stage=9;
        }else if(stage==9 && CruiseSky.buffered()){
            require(!CruiseSky.ready(),"preview arrived before crossing Y=256");
            work=mc.getIntegratedServer().addScheduledTask(CruiseRuntimeChecks::enter);stage=2;
        }else if(stage==2&&mc.player.getRidingEntity() instanceof EntityGroundVehicle&&CruiseSky.detailed()){
            mc.gameSettings.thirdPersonView=0;
            stage=6;ticks=0;
        }else if(stage==6&&ticks>10){
            require(VehicleClient.cruisePilotDraws()==0,"first-person cruise leaves the pilot out of the main render pass");
            if (System.getenv("VANDOR_LABS_TEST_JOURNEYMAP")!=null) verifyJourneyMap(mc);
            mc.gameSettings.thirdPersonView=1;
            mc.player.rotationPitch=mc.player.prevRotationPitch=35;
            mc.gameSettings.hideGUI=true;stage=3;ticks=0;
        }else if(stage==3&&ticks>15){
            int pilotDraws=VehicleClient.cruisePilotDraws();
            require(pilotDraws==1,"third-person cruise draws the local pilot exactly once, draws="+pilotDraws);
            BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            ImageIO.write(shot,"png",new File(out,"maxwell-cruise.png"));
            if("write".equals(RESTART)){
                work=mc.getIntegratedServer().addScheduledTask(()->saveFlight(mc));stage=5;return false;
            }
            work=mc.getIntegratedServer().addScheduledTask(()->{
                checkRoute();
                require(craft.cruise.active(),"craft still cruising");
                require(craft.cruise.move(0,255-craft.cruise.altitude(),0),"reenter");
            });stage=4;
        }else if(stage==4&&mc.player.getRidingEntity() instanceof EntityGroundVehicle&&Math.abs(mc.player.posX-destination)<4&&mc.player.posY<300){
            work=mc.getIntegratedServer().addScheduledTask(()->{
                require(!craft.cruise.active()&&Math.abs(craft.posX-destination)<.01,"reentered at geographic destination");
                require(craft.world.isBlockLoaded(new BlockPos(destination,64,craft.posZ)),"destination prepared");
                require(!craft.world.isBlockLoaded(new BlockPos(startX+2048,64,craft.posZ)),"middle of route stayed unloaded");
                EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                int result=mc.getIntegratedServer().getCommandManager().executeCommand(mc.getIntegratedServer(),
                        "tp "+pilot.getName()+" "+(int)startX+" 85 0");
                require(result==1 && !pilot.isRiding() && Math.abs(pilot.posX-startX)<2,"vanilla tp releases pilot");
                require(!craft.flying() && !craft.hover && !craft.cruise.active() && craft.onGround
                        && Math.abs(craft.posX-destination)<2 && craft.posY<255,"last pilot leaves craft landed at route");
            });stage=7;
        }else if(stage==7 || stage==9) {
            if (mc.player.isRiding() || Math.abs(mc.player.posX-startX)>=2 || Math.abs(mc.player.posZ)>=2) {
                teleportSettled=0;
                return false;
            }
            if (++teleportSettled<12) return false;
            if (stage==7 && System.getenv("VANDOR_LABS_TEST_JOURNEYMAP")!=null) {
                work=mc.getIntegratedServer().addScheduledTask(()->prepareJourneyMapTeleport(mc));
                teleportSettled=0;
                stage=8;
                return false;
            }
            work=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                require(!pilot.isRiding() && Math.abs(pilot.posX-startX)<2 && Math.abs(pilot.posZ)<2,
                        "authoritative rider remains at the external teleport destination");
            });
            stage=10;
        }else if(stage==8 && ticks>8 && mc.player.getRidingEntity() instanceof EntityGroundVehicle) {
            EntityGroundVehicle mounted=(EntityGroundVehicle)mc.player.getRidingEntity();
            if(!mounted.getUniqueID().equals(craft.getUniqueID()) || !mounted.cruise.active())return false;
            work=mc.getIntegratedServer().addScheduledTask(()->journeyMapTeleport(mc));
            stage=9;
        }else if(stage==10) {
            System.out.println("[vandorlabs] maxwell-cruise PASS route-unloaded rider-seed-view saved-route destination-remount settled-external-teleport");
            return true;
        }
        if(stage==5 && work==null){
            System.out.println("[vandorlabs] maxwell-cruise PASS saved mounted pilot in flight for a cold restart");
            return true;
        }
        return false;
    }
    private static void prepareJourneyMapTeleport(Minecraft mc) {
        place(mc);
        NBTTagCompound state=new NBTTagCompound();
        state.setByte("MaxwellFlightPhase",(byte)3);
        craft.readFlightMode(state);
        require(craft.cruise.move(2048,512,0),"enter high cruise before waypoint teleport");
    }
    private static void journeyMapTeleport(Minecraft mc) {
        double site=craft.cruise.x();
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        try {
            Class<?> location=Class.forName("journeymap.common.feature.Location");
            Object target=location.getConstructor(double.class,double.class,double.class,int.class)
                    .newInstance(startX,85.0,0.0,0);
            Class<?> feature=Class.forName("journeymap.common.feature.JourneyMapTeleport");
            boolean accepted=(Boolean)feature.getMethod("attemptTeleport",net.minecraft.entity.Entity.class,location)
                    .invoke(feature.getMethod("instance").invoke(null),pilot,target);
            require(accepted && !pilot.isRiding() && Math.abs(pilot.posX-startX)<2,"JourneyMap releases and relocates pilot");
            require(!craft.cruise.active() && !craft.flying() && craft.onGround && craft.posY<255
                    && Math.abs(craft.posX-site)<1 && craft.chunkCoordX==((int)Math.floor(site)>>4),
                    "JourneyMap lands and reindexes the aircraft at its geographic position");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    static void verifyJourneyMap(Minecraft mc) throws Exception {
        Class<?> manager=Class.forName("journeymap.client.ui.UIManager");
        Object minimap=manager.getMethod("getMiniMap").invoke(manager.getField("INSTANCE").get(null));
        String location=(String)minimap.getClass().getMethod("getLocation").invoke(minimap);
        int route=net.minecraft.util.math.MathHelper.floor(com.vandorlabs.client.CruiseMapView.x(mc.player.posX));
        Class<?> waypoint=Class.forName("journeymap.client.model.Waypoint");
        Object mark=waypoint.getMethod("of",net.minecraft.entity.player.EntityPlayer.class).invoke(null,mc.player);
        require(((Integer)waypoint.getMethod("getX").invoke(mark))==route,"JourneyMap new waypoint follows route");
        int altitude=net.minecraft.util.math.MathHelper.floor(com.vandorlabs.client.CruiseMapView.y(mc.player.posY));
        require(((Integer)waypoint.getMethod("getY").invoke(mark))==altitude,"JourneyMap new waypoint uses flight altitude");
        require(location.contains(String.valueOf(route)),"JourneyMap minimap location "+location+" expected X "+route);
    }
    private CruiseRuntimeChecks(){}
}
