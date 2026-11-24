package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.config.BlockAvailability;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Connected-player saves, cold restore, carried controls and region-file evidence in geographic High Flight. */
@SideOnly(Side.CLIENT)
final class CruisePhysicalChecks {
    private static final boolean ENABLED=Boolean.getBoolean("vandorlabs.cruisePhysicalChecks");
    private static final int FAR=8192, SWEEP=768, STEP=12;
    private static final String RESTART=System.getenv("VANDOR_LABS_CRUISE_PHASE");
    private static final boolean WARM="true".equals(System.getenv("VANDOR_LABS_WARM_APPROACH"));
    private static final String EXIT=System.getenv("VANDOR_LABS_PHYSICAL_EXIT");
    private static final String MODE=System.getenv().getOrDefault("VANDOR_LABS_REENTRY_MODE","physical");
    private static final int BAND_HANDOFF=Integer.parseInt(System.getenv().getOrDefault("VANDOR_LABS_BAND_ROUTE_HANDOFF","0"));
    private static final int BAND_STEPS=96,BAND_STRIDE=6,BAND_HEIGHT=384;
    private static final String ANCHOR_FIXTURE="PhysicalCruiseFixture";
    private static final Audit AUDIT=new Audit();
    private static EntityGroundVehicle craft;
    private static EntityPlayerMP pilot;
    private static WorldServer world;
    private static Future<?> work;
    private static int stage, wait, steps, startingTicks;
    private static double startX,startZ,remoteX,arrivalX;
    private static double lastCraftX=Double.NaN,firstArrivalY;
    private static int smoothSamples, mountedArrivalTicks;
    private static long exitTick;
    private static volatile boolean observingDescent;
    private static double approachStartX,previousApproachX;
    private static int movingApproachTicks;
    private static long approachTickStart, longestWorldTick;
    private static double maximumWaypointMismatch;
    private static int firstTerrainTick=-1,firstDescentTick=-1;

    private CruisePhysicalChecks() {}
    static boolean enabled(){return ENABLED;}
    private static boolean physical(){return "physical".equals(MODE);}
    private static void require(boolean valid,String reason){
        if(!valid)throw new IllegalStateException("Physical cruise stage="+stage+" step="+steps+": "+reason);
    }

    static boolean tick(Minecraft mc,File output)throws Exception {
        if(Boolean.getBoolean("vandorlabs.cabinWalkChecks"))return CabinWalkChecks.tick(mc);
        require(++wait<680,"timed out rider="+mc.player.getRidingEntity()+" x="+mc.player.posX);
        if(work!=null){
            if(!work.isDone())return false;
            work.get();
            work=null;
        }
        if(stage==0){
            // ReproLab owns this isolated runtime; exercise the same Forge Configuration path as a server restart.
            File file=new File(mc.mcDataDir,"config/cruise-physical-fixture.cfg");
            net.minecraftforge.common.config.Configuration config=new net.minecraftforge.common.config.Configuration(file);
            config.load();
            config.get("vehicles","cruiseCoordinates","anchored").set("resident");
            config.get("vehicles","reanchorResidentCruise",false).set(true);
            config.save();
            BlockAvailability.load(file);
            require(BlockAvailability.cruiseCoordinates()==BlockAvailability.CruiseCoordinates.ANCHORED,
                    "retired Resident config uses anchored cruise");
            config=new net.minecraftforge.common.config.Configuration(file);
            config.load();
            require("anchored".equals(config.get("vehicles","cruiseCoordinates","anchored").getString())
                    &&!config.hasKey("vehicles","reanchorResidentCruise"),"retired selection rewritten on disk");
            config.get("vehicles","cruiseCoordinates","anchored").set(MODE);
            if(BAND_HANDOFF!=0)config.get("vehicles","cruiseHandoffAltitude",512).set(BAND_HANDOFF);
            config.save();
            BlockAvailability.load(file);
            require(BlockAvailability.cruiseCoordinates().name().equalsIgnoreCase(MODE),
                    "effective server config selects "+MODE+" coordinates");
            mc.displayGuiScreen(null);
            if("read".equals(RESTART)){
                work=mc.getIntegratedServer().addScheduledTask(()->resume(mc));
                advance(2);
            }else{
                work=mc.getIntegratedServer().addScheduledTask(()->place(mc));
                advance(1);
            }
        }else if(stage==1&&wait>15&&mc.player.getRidingEntity() instanceof EntityGroundVehicle){
            if(BAND_HANDOFF!=0){
                work=mc.getIntegratedServer().addScheduledTask(CruisePhysicalChecks::startBand);
                advance(6);
                return false;
            }
            work=mc.getIntegratedServer().addScheduledTask(CruisePhysicalChecks::start);
            advance(2);
        }else if(stage==2&&wait>=5){
            if(steps<SWEEP/STEP){
                if(steps==35)com.vandorlabs.client.CruiseWaypointChecks.check(mc);
                if(physical()&&steps>12&&steps<60)samplePhysicalMotion(mc);
                work=mc.getIntegratedServer().addScheduledTask(CruisePhysicalChecks::step);
            }else{
                require(mc.player.getRidingEntity() instanceof EntityGroundVehicle,"real client stayed mounted");
                if(physical()&&Math.abs(mc.player.posX-(remoteX+SWEEP))>=20){
                    if(wait%40==0){
                        EntityGroundVehicle clientCraft=(EntityGroundVehicle)mc.player.getRidingEntity();
                        BlockPos anchor=new BlockPos(clientCraft.cruise.anchorX(),64,clientCraft.cruise.anchorZ());
                        System.out.println("[vandorlabs][coordinate-probe] waiting client riderX="+mc.player.posX+
                            " clientCraftX="+clientCraft.posX+" active="+clientCraft.cruise.active()+
                            " indexed="+clientCraft.addedToChunk+" anchorLoaded="+mc.world.isBlockLoaded(anchor)+
                            " clientCraftTicks="+clientCraft.ticksExisted+" passengers="+clientCraft.getPassengers().size());
                    }
                    return false;
                }
                if(physical()&&!CruiseProjectionGameplayChecks.tick(mc,output,craft))return false;
                if(physical()&&"descend".equals(EXIT))require(smoothSamples>=8,
                    "the mounted aircraft interpolated repeated high-flight snapshots, samples="+smoothSamples);
                System.out.println("[vandorlabs][coordinate-probe] client playerX="+mc.player.posX+
                    " craftX="+mc.player.getRidingEntity().posX+" playerChunk="+mc.player.chunkCoordX);
                work=mc.getIntegratedServer().addScheduledTask(CruisePhysicalChecks::finish);
                advance(3);
            }
        }else if(stage==3&&EXIT!=null){
            work=mc.getIntegratedServer().addScheduledTask(CruisePhysicalChecks::exit);
            advance(4);
        }else if(stage==4){
            if(clientExitSettled(mc)) {
                if("descend".equals(EXIT)) {
                    javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(
                        mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"cruise-arrival-"+MODE+".png"));
                    double clientY=mc.player.getRidingEntity().posY;
                    work=mc.getIntegratedServer().addScheduledTask(()->verifyArrival(clientY));
                }
                advance(5);
            } else if(wait%40==0) {
                double clientY=mc.player.posY;
                work=mc.getIntegratedServer().addScheduledTask(()->System.out.println(
                    "[vandorlabs][cruise-arrival] waiting serverTicks="+(world.getTotalWorldTime()-exitTick)
                    +" serverY="+craft.posY+" clientY="+clientY+" active="+craft.cruise.active()
                    +" pitch="+craft.flightPitch()+" bank="+craft.flightBank()+" motionY="+craft.motionY));
            }
        }else if(stage==6){
            if(steps<BAND_STEPS){
                work=mc.getIntegratedServer().addScheduledTask(CruisePhysicalChecks::moveInBand);
            }else if(wait>BAND_STEPS+30){
                require(mc.player.getRidingEntity() instanceof EntityGroundVehicle,"pilot still mounted during band travel");
                EntityGroundVehicle local=(EntityGroundVehicle)mc.player.getRidingEntity();
                require(local.cruise.handoffAltitude()==BAND_HANDOFF,"server handoff setting reached mounted client");
                work=mc.getIntegratedServer().addScheduledTask(CruisePhysicalChecks::finishBand);
                advance(5);
            }
        }else if(stage==3||stage==5){
            System.out.println("[vandorlabs] cruise-physical PASS");
            return true;
        }
        return false;
    }

    private static void advance(int next){stage=next;wait=0;}

    private static void place(Minecraft mc){
        pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        world=pilot.getServerWorld();
        BlockPos seat=BlockPos.ORIGIN;
        craft=new EntityGroundVehicle(world);
        ArrayList<VehicleStructure.Cell> cells=new ArrayList<>(Arrays.asList(
            new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null),
            new VehicleStructure.Cell(seat.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null)));
        cells.addAll(CruiseProjectionGameplayChecks.cells());
        craft.install(new VehicleStructure(cells,seat,EnumFacing.NORTH,1));
        startX=pilot.posX;
        startZ=pilot.posZ;
        remoteX=startX+FAR;
        craft.setPosition(startX,511.9,startZ);
        world.spawnEntity(craft);
        pilot.setPositionAndUpdate(startX,512,startZ);
        craft.assignSeat(pilot,seat);
        require(pilot.startRiding(craft,true),"real pilot mounts");
        craft.hover=true;
        craft.hoverTarget=511.9;
    }

    private static void startBand(){
        require(BAND_HANDOFF>=256&&BAND_HANDOFF<=512,"supported band setting");
        require(!generated(startX+BAND_STEPS*BAND_STRIDE,startZ),"fresh destination before band travel");
        craft.setPosition(startX,BAND_HEIGHT,startZ);
        NBTTagCompound state=new NBTTagCompound();
        state.setByte("MaxwellFlightPhase",(byte)3);
        craft.readFlightMode(state);
        MinecraftForge.EVENT_BUS.register(AUDIT);
    }

    /** Move the real mounted watch origin six blocks per sample at a constant geographic altitude. */
    private static void moveInBand(){
        double rise=BAND_HEIGHT-craft.flightAltitude();
        if(!craft.cruise.move(BAND_STRIDE,rise,0))craft.setPosition(startX+(steps+1)*BAND_STRIDE,BAND_HEIGHT,startZ);
        craft.updatePassenger(pilot);
        world.updateEntityWithOptionalForce(craft,false);
        world.getMinecraftServer().getPlayerList().serverUpdateMovingPlayer(pilot);
        VehicleNetwork.state(craft,true);
        steps++;
    }

    private static void finishBand(){
        MinecraftForge.EVENT_BUS.unregister(AUDIT);
        boolean high=BAND_HEIGHT>BAND_HANDOFF;
        require(craft.cruise.active()==high,"band travel selects the configured tracking mode");
        require(high?AUDIT.loads.isEmpty():!AUDIT.loads.isEmpty(),"expected native wilderness column activity");
        System.out.println("[vandorlabs][flight-band] handoff="+BAND_HANDOFF+" altitude="+BAND_HEIGHT+
                " distance="+(BAND_STRIDE*BAND_STEPS)+" freshRouteLoads="+AUDIT.loads.size());
    }

    private static void resume(Minecraft mc){
        pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        world=pilot.getServerWorld();
        NBTTagCompound marker=pilot.getEntityData().getCompoundTag(ANCHOR_FIXTURE);
        require(marker.hasUniqueId("Aircraft"),"cold restart restores the player test marker");
        Entity restored=world.getEntityFromUuid(marker.getUniqueId("Aircraft"));
        require(pilot.getRidingEntity() instanceof EntityGroundVehicle,"cold restart restores the saved mounting; "
            +"index reports "+restored+" rider="+pilot.posX+","+pilot.posY+","+pilot.posZ);
        craft=(EntityGroundVehicle)pilot.getRidingEntity();
        require(craft.getUniqueID().equals(marker.getUniqueId("Aircraft"))&&craft.cruise.active(),"same active saved aircraft");
        startX=marker.getDouble("AnchorX");startZ=marker.getDouble("AnchorZ");remoteX=marker.getDouble("RouteX");
        require(craft.cruise.anchorX()==startX&&Math.abs(craft.cruise.x()-remoteX)<.01&&Math.abs(pilot.managedPosX-startX)<3,
            "cold restart restores separate canonical route and actual tracking anchor");
        require(!generated(remoteX,startZ),"resume already loaded the geographic route");
        startingTicks=craft.ticksExisted;
        MinecraftForge.EVENT_BUS.register(AUDIT);
    }

    private static void start(){
        require(!generated(remoteX,startZ)&&!generated(remoteX+SWEEP,startZ),"remote witness columns start without terrain");
        MinecraftForge.EVENT_BUS.register(AUDIT);
        NBTTagCompound restoredFlight=new NBTTagCompound();
        restoredFlight.setByte("MaxwellFlightPhase",(byte)3);
        craft.readFlightMode(restoredFlight);
        craft.setPosition(startX,511.9,startZ);
        require(craft.cruise.move(0,1,0),"enter high flight");
        require(craft.cruise.move(FAR,640,0),"advance geographic route");
        startingTicks=craft.ticksExisted;
        world.getMinecraftServer().getPlayerList().serverUpdateMovingPlayer(pilot);
    }

    private static void step(){
        require(craft.cruise.active(),"cruise remains active");
        require(craft.cruise.move(STEP,0,0),"route advances");
        steps++;
        craft.updatePassenger(pilot);
        world.getMinecraftServer().getPlayerList().serverUpdateMovingPlayer(pilot);
        if(steps==16)world.getPlayerChunkMap().setPlayerViewRadius(8);
        if(steps==32)world.getPlayerChunkMap().setPlayerViewRadius(10);
        require(craft.cruise.projected()==physical() && Math.abs(craft.posX-(physical()?craft.cruise.x():startX))<.001
            &&Math.abs(craft.chunkCoordX-(int)Math.floor(startX/16))<=1,"craft remains indexed at departure");
        require(Math.abs(pilot.posX-(physical()?craft.cruise.x():startX))<3,"unexpected actual server player x="+pilot.posX);
        require(Math.abs(pilot.managedPosX-startX)<3 && Math.abs(pilot.chunkCoordX-(int)Math.floor(startX/16))<=1,
            "server player remains in approved departure tracking");
    }

    private static void finish(){
        NBTTagCompound vehicle=craft.writeToNBT(new NBTTagCompound());
        require(vehicle.getTagList("Pos",6).getDoubleAt(0)==startX
            &&Math.abs(pilot.writeToNBT(new NBTTagCompound()).getTagList("Pos",6).getDoubleAt(0)-startX)<3,
            "aircraft and rider persist their real anchor, independently of live geographic coordinates");
        if("write".equals(RESTART)){
            NBTTagCompound marker=new NBTTagCompound();
            marker.setUniqueId("Aircraft",craft.getUniqueID());
            marker.setDouble("AnchorX",startX);marker.setDouble("AnchorZ",startZ);marker.setDouble("RouteX",craft.cruise.x());
            pilot.getEntityData().setTag(ANCHOR_FIXTURE,marker);
            world.getMinecraftServer().getPlayerList().saveAllPlayerData();
        }
        try{
            world.saveAllChunks(true,null);
            world.getChunkProvider().flushToDisk();
        }catch(net.minecraft.world.MinecraftException failure){throw new IllegalStateException("Audit checkpoint failed",failure);}
        MinecraftForge.EVENT_BUS.unregister(AUDIT);
        boolean loaded=generated(remoteX,startZ), disk=onDisk(remoteX,startZ);
        require(craft.ticksExisted>startingTicks+20,"craft really ticks during transit");
        require(pilot.getRidingEntity()==craft,"server pilot remains mounted");
        require(AUDIT.loads.isEmpty()&&AUDIT.saves.isEmpty()&&!loaded&&!disk,
            "route terrain was requested: loads="+AUDIT.loads.size()+" saves="+AUDIT.saves.size()+" first="+AUDIT.firstLoad);
        for(int offset=0;offset<=SWEEP;offset+=16)for(int dz=-96;dz<=96;dz+=16)
            require(!generated(remoteX+offset,startZ+dz)&&!onDisk(remoteX+offset,startZ+dz),"unexpected disk or loaded chunk at "+offset+","+dz);
        System.out.println("[vandorlabs][physical-cruise] remoteLoads="+AUDIT.loads.size()+
            " remoteSaves="+AUDIT.saves.size()+" generatedWitness="+loaded+" diskWitness="+disk+" playerX="+pilot.posX+
            " managedX="+pilot.managedPosX+" craftX="+craft.posX+" craftTicks="+(craft.ticksExisted-startingTicks)+
            " savedPlayerX="+pilot.writeToNBT(new NBTTagCompound()).getTagList("Pos",6).getDoubleAt(0)+
            (AUDIT.firstLoad==null?"":" firstLoad="+AUDIT.firstLoad));
    }

    private static void samplePhysicalMotion(Minecraft mc){
        require(mc.player.getRidingEntity() instanceof EntityGroundVehicle,"the pilot stays mounted at speed");
        EntityGroundVehicle local=(EntityGroundVehicle)mc.player.getRidingEntity();
        double delta=local.posX-lastCraftX;
        if(delta>.25&&delta<STEP*1.6)smoothSamples++;
        if(System.getenv("VANDOR_LABS_TEST_JOURNEYMAP")!=null){
            net.minecraft.client.renderer.entity.RenderManager camera=mc.getRenderManager();
            net.minecraft.util.math.Vec3d position=mc.player.getPositionVector();
            double separation=Math.abs(camera.viewerPosX-position.x);
            if(separation<STEP*2)maximumWaypointMismatch=Math.max(maximumWaypointMismatch,separation);
            net.minecraft.util.math.Vec3d origin=com.vandorlabs.client.CruiseMapView.beaconOrigin(position);
            require(origin.x==camera.viewerPosX && origin.z==camera.viewerPosZ
                    && origin.y==com.vandorlabs.client.CruiseMapView.y(camera.viewerPosY),
                    "distant waypoint clamp and render translation share the interpolated client camera");
        }
        lastCraftX=local.posX;
    }

    private static void exit(){
        require(craft.cruise.active()&&craft.cruise.projected()==physical()&&pilot.getRidingEntity()==craft,
            "exit begins in selected high flight");
        exitTick=world.getTotalWorldTime();
        arrivalX=craft.cruise.x();
        if("descend".equals(EXIT)) {
            if(WARM){
                // Model a landing site that has already been visited and remains loaded.
                // Prepare it after the independent high-flight route audit has finished.
                int cx=(int)Math.floor(arrivalX/16),cz=(int)Math.floor(craft.cruise.z()/16);
                for(int x=cx-3;x<=cx+6;x++)for(int z=cz-3;z<=cz+3;z++)
                    world.getChunkProvider().provideChunk(x,z);
                require(world.getChunkProvider().getLoadedChunk(cx,cz).isTerrainPopulated(),"warm approach fixture");
            }
            // Start an eastbound glide over fresh columns. Subsequent movement comes from normal server flight ticks.
            craft.rotationYaw=90;
            NBTTagCompound flight=new NBTTagCompound();
            flight.setByte("MaxwellFlightPhase",(byte)EntityGroundVehicle.GLIDING_FLIGHT);
            flight.setDouble("MaxwellFlightSpeed",1.5);
            flight.setFloat("MaxwellFlightPitch",-18);
            craft.readFlightMode(flight);
            require(craft.cruise.move(1.4,craft.cruise.handoffAltitude()-1-craft.cruise.altitude(),0,90,-18,0),"dive through the cruise boundary");
            arrivalX=approachStartX=previousApproachX=craft.cruise.x();
            observingDescent=true;
            MinecraftForge.EVENT_BUS.register(AUDIT);
        }else if("teleport".equals(EXIT)) {
            int result=world.getMinecraftServer().getCommandManager().executeCommand(world.getMinecraftServer(),
                "tp "+pilot.getName()+" "+(int)startX+" 100 "+(int)startZ);
            require(result==1&&!pilot.isRiding()&&craft.onGround&&craft.posY<256,
                "actual last-pilot /tp lands the aircraft below the geographic position");
            verifyDestination();
        }else throw new IllegalStateException("Unknown cruise exit: "+EXIT);
    }

    private static void verifyArrival(double clientY){
        require(pilot.getRidingEntity()==craft&&craft.posY<512,"descent continues underneath the ceiling with pilot aboard");
        require(Math.abs(clientY-craft.posY)<4,"server and visible aircraft continue through the same descent");
        require(world.getTotalWorldTime()-exitTick<240,"reentry keeps updating the client promptly");
        require(movingApproachTicks>1&&arrivalX>approachStartX+1,"glide traveled while preparing new terrain");
        int cx=(int)Math.floor(arrivalX/16),cz=(int)Math.floor(startZ/16);
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)
            require(world.getPlayerChunkMap().isPlayerWatchingChunk(pilot,x,z),"arrival footprint delivered at "+x+","+z);
        verifyDestination();
    }

    private static void verifyDestination(){
        boolean descending="descend".equals(EXIT);
        require(!craft.cruise.active()&&(descending?craft.posX>=arrivalX-.01:Math.abs(craft.posX-arrivalX)<.01)
            &&craft.chunkCoordX==(int)Math.floor(craft.posX/16),"aircraft reindexes at its moving geographic position");
        require(generated(arrivalX,startZ)&&!generated(startX+FAR/2,startZ),
            "the destination is loaded and the route midpoint is still absent");
        System.out.println("[vandorlabs][cruise-arrival] mode="+MODE+" exit="+EXIT+" arrivalX="+arrivalX+
            " midpointUnloaded=true mountedArrivalTicks="+mountedArrivalTicks+" serverTicks="+(world.getTotalWorldTime()-exitTick)
            +" smoothSamples="+smoothSamples+" firstTerrainClientTick="+firstTerrainTick
            +" firstDescentClientTick="+firstDescentTick+" previousBeaconOriginMismatch="+maximumWaypointMismatch);
    }

    private static boolean clientExitSettled(Minecraft mc){
        if("teleport".equals(EXIT))return !mc.player.isRiding()&&Math.abs(mc.player.posX-startX)<2&&mc.player.posY<120;
        int arrivalColumn=(int)Math.floor(arrivalX/16), arrivalRow=(int)Math.floor(startZ/16);
        if(firstTerrainTick<0&&mc.world.getChunkProvider().getLoadedChunk(arrivalColumn,arrivalRow)!=null)
            firstTerrainTick=wait;
        if(mc.player.posY>=550)return false;
        require(mc.player.getRidingEntity() instanceof EntityGroundVehicle,"actual client kept the mounted craft at reentry");
        EntityGroundVehicle local=(EntityGroundVehicle)mc.player.getRidingEntity();
        require(local.getUniqueID().equals(craft.getUniqueID())&&local.view!=null,"client mount retains actual aircraft structure");
        if(local.cruise.active()||observingDescent||mc.player.posX<arrivalX-4)return false;
        int cx=(int)Math.floor(arrivalX/16),cz=(int)Math.floor(startZ/16);
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)
            require(mc.world.getChunkProvider().getLoadedChunk(x,z)!=null,"arrival terrain present on first client descent frame "+x+","+z);
        require(local.addedToChunk&&local.isAddedToWorld()&&mc.world.getEntityByID(local.getEntityId())==local
            &&local.getPassengers().contains(mc.player),"client aircraft remains indexed and mounted");
        if(mountedArrivalTicks++==0){
            require(mc.world.getChunkProvider().getLoadedChunk(cx+2,cz)!=null,"two columns ahead available on first client arrival frame");
            firstDescentTick=wait;
            firstArrivalY=local.posY;
            mc.gameSettings.thirdPersonView=1;
            mc.player.rotationPitch=mc.player.prevRotationPitch=18;
        }
        return mountedArrivalTicks>=20&&local.posY<512&&local.posY<firstArrivalY-.5;
    }

    private static boolean generated(double x,double z){
        return world.getChunkProvider().isChunkGeneratedAt((int)Math.floor(x/16),(int)Math.floor(z/16));
    }

    private static boolean onDisk(double blockX,double blockZ){
        int x=(int)Math.floor(blockX/16),z=(int)Math.floor(blockZ/16);
        File directory=((AnvilChunkLoader)world.getChunkProvider().chunkLoader).chunkSaveLocation;
        File file=new File(new File(directory,"region"),"r."+(x>>5)+"."+(z>>5)+".mca");
        if(!file.isFile())return false;
        try(RandomAccessFile data=new RandomAccessFile(file,"r")){
            data.seek(((z&31)*32+(x&31))*4L);
            return data.readInt()!=0;
        }catch(IOException failure){throw new IllegalStateException("Cannot read independent Anvil location table",failure);}
    }

    @SideOnly(Side.CLIENT)
    public static final class Audit {
        private final Set<ChunkPos> loads=new LinkedHashSet<>(), saves=new LinkedHashSet<>();
        private String firstLoad;

        @SubscribeEvent public void observeApproach(net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent event){
            if(!observingDescent||event.world!=world)return;
            if(event.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START){
                approachTickStart=System.nanoTime();
                return;
            }
            if(approachTickStart!=0)longestWorldTick=Math.max(longestWorldTick,System.nanoTime()-approachTickStart);
            double x=craft.cruise.x();
            // A scheduled setup may run after the world's entity tick.
            if(world.getTotalWorldTime()==exitTick&&x==approachStartX)return;
            require(x>previousApproachX+.1,"aircraft stopped waiting for arrival terrain at x="+x+" previous="+previousApproachX);
            movingApproachTicks++;
            previousApproachX=x;
            if(craft.cruise.active())return;
            arrivalX=x;
            int cx=(int)Math.floor(x/16),cz=(int)Math.floor(craft.posZ/16);
            require(world.getPlayerChunkMap().isPlayerWatchingChunk(pilot,cx+2,cz),"two forward approach columns ready at handoff");
            observingDescent=false;
            MinecraftForge.EVENT_BUS.unregister(this);
        }

        private boolean remote(ChunkEvent event){
            double x=event.getChunk().x*16.0,z=event.getChunk().z*16.0;
            return event.getWorld()==world && x>=startX+(BAND_HANDOFF!=0?256:1024) && x<=remoteX+SWEEP+1024 && Math.abs(z-startZ)<=1024;
        }
        @SubscribeEvent public void load(ChunkEvent.Load event){
            if(!remote(event))return;
            loads.add(event.getChunk().getPos());
            if(firstLoad==null){
                StringBuilder description=new StringBuilder(event.getChunk().getPos().toString());
                for(StackTraceElement frame:Thread.currentThread().getStackTrace())
                    if(frame.getClassName().startsWith("net.minecraft.")){
                        description.append(" ").append(frame.getClassName()).append(".").append(frame.getMethodName());
                        if(description.length()>=420)break;
                    }
                firstLoad=description.toString();
            }
        }
        @SubscribeEvent public void save(ChunkDataEvent.Save event){if(remote(event))saves.add(event.getChunk().getPos());}
    }
}
