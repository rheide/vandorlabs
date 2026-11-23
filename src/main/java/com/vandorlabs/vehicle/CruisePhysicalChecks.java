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
    private static final String EXIT=System.getenv("VANDOR_LABS_PHYSICAL_EXIT");
    private static final String MODE=System.getenv().getOrDefault("VANDOR_LABS_REENTRY_MODE","physical");
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

    private CruisePhysicalChecks() {}
    static boolean enabled(){return ENABLED;}
    private static boolean physical(){return "physical".equals(MODE);}
    private static void require(boolean valid,String reason){
        if(!valid)throw new IllegalStateException("Physical cruise stage="+stage+" step="+steps+": "+reason);
    }

    static boolean tick(Minecraft mc,File output)throws Exception {
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
            config.get("vehicles","cruiseCoordinates","anchored").set(MODE);
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
            work=mc.getIntegratedServer().addScheduledTask(CruisePhysicalChecks::start);
            advance(2);
        }else if(stage==2&&wait>=5){
            if(steps<SWEEP/STEP){
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
        lastCraftX=local.posX;
    }

    private static void exit(){
        require(craft.cruise.active()&&craft.cruise.projected()==physical()&&pilot.getRidingEntity()==craft,
            "exit begins in selected high flight");
        exitTick=world.getTotalWorldTime();
        arrivalX=craft.cruise.x();
        if("descend".equals(EXIT)) {
            arrivalX+=24;
            require(craft.cruise.move(24,511-craft.cruise.altitude(),0,18,-32,25),"dive through the cruise boundary");
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
        require(world.getTotalWorldTime()-exitTick<120,"reentry keeps updating the client promptly");
        int cx=(int)Math.floor(arrivalX/16),cz=(int)Math.floor(startZ/16);
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)
            require(world.getPlayerChunkMap().isPlayerWatchingChunk(pilot,x,z),"arrival footprint delivered at "+x+","+z);
        verifyDestination();
    }

    private static void verifyDestination(){
        require(!craft.cruise.active()&&Math.abs(craft.posX-arrivalX)<.01
            &&craft.chunkCoordX==(int)Math.floor(arrivalX/16),"aircraft reindexes at the real arrival site");
        require(generated(arrivalX,startZ)&&!generated(startX+FAR/2,startZ),
            "the destination is loaded and the route midpoint is still absent");
        System.out.println("[vandorlabs][cruise-arrival] mode="+MODE+" exit="+EXIT+" arrivalX="+arrivalX+
            " midpointUnloaded=true mountedArrivalTicks="+mountedArrivalTicks+" serverTicks="+(world.getTotalWorldTime()-exitTick)
            +" smoothSamples="+smoothSamples);
    }

    private static boolean clientExitSettled(Minecraft mc){
        if("teleport".equals(EXIT))return !mc.player.isRiding()&&Math.abs(mc.player.posX-startX)<2&&mc.player.posY<120;
        if(mc.player.posY>=550)return false;
        require(mc.player.getRidingEntity() instanceof EntityGroundVehicle,"actual client kept the mounted craft at reentry");
        EntityGroundVehicle local=(EntityGroundVehicle)mc.player.getRidingEntity();
        require(local.getUniqueID().equals(craft.getUniqueID())&&local.view!=null,"client mount retains actual aircraft structure");
        if(local.cruise.active()||Math.abs(mc.player.posX-arrivalX)>=4)return false;
        int cx=(int)Math.floor(arrivalX/16),cz=(int)Math.floor(startZ/16);
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)
            require(mc.world.getChunkProvider().getLoadedChunk(x,z)!=null,"arrival terrain present on first client descent frame "+x+","+z);
        require(local.addedToChunk&&local.isAddedToWorld()&&mc.world.getEntityByID(local.getEntityId())==local
            &&local.getPassengers().contains(mc.player),"client aircraft remains indexed and mounted");
        if(mountedArrivalTicks++==0){
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

        private boolean remote(ChunkEvent event){
            double x=event.getChunk().x*16.0,z=event.getChunk().z*16.0;
            return event.getWorld()==world && x>=startX+1024 && x<=remoteX+SWEEP+1024 && Math.abs(z-startZ)<=1024;
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
