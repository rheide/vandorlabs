package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.config.BlockAvailability;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** A connected client follows a real resident hop and stays at that anchor over a fresh route. */
@SideOnly(Side.CLIENT)
final class CruiseResidentChecks {
    private static final int FAR=8192;
    private static final Audit AUDIT=new Audit();
    private static EntityGroundVehicle craft;
    private static EntityPlayerMP pilot;
    private static WorldServer world;
    private static Future<?> work;
    private static int stage,ticks,idleSaves;
    private static double departure, residentX, wildernessX, originZ;

    private CruiseResidentChecks() {}

    private static void require(boolean condition,String detail){
        if(!condition)throw new IllegalStateException("Resident cruise stage="+stage+" tick="+ticks+": "+detail);
    }

    static boolean tick(Minecraft client)throws Exception{
        require(++ticks<740,"timed out playerX="+client.player.posX+" mount="+client.player.getRidingEntity());
        if(work!=null){
            if(!work.isDone())return false;
            work.get();work=null;
        }
        if(stage==0){
            client.displayGuiScreen(null);
            client.gameSettings.renderDistanceChunks=3;
            work=client.getIntegratedServer().addScheduledTask(()->prepare(client));
            advance(1);
        }else if(stage==1&&ticks>=12&&client.player.getRidingEntity() instanceof EntityGroundVehicle){
            work=client.getIntegratedServer().addScheduledTask(CruiseResidentChecks::enter);
            advance(2);
        }else if(stage==2&&client.player.getRidingEntity() instanceof EntityGroundVehicle
            && Math.abs(client.player.posX-residentX)<4 && Math.abs(client.player.getRidingEntity().posX-residentX)<1){
            work=client.getIntegratedServer().addScheduledTask(CruiseResidentChecks::crossWilderness);
            advance(3);
        }else if(stage==3&&ticks>=35&&idleSaves==0){
            work=client.getIntegratedServer().addScheduledTask(CruiseResidentChecks::checkpoint);
        }else if(stage==3&&ticks>=90){
            require(client.player.getRidingEntity() instanceof EntityGroundVehicle
                && Math.abs(client.player.posX-residentX)<4 && Math.abs(client.player.getRidingEntity().posX-residentX)<1,
                "client and carried hull stay together at the last resident site over wilderness");
            work=client.getIntegratedServer().addScheduledTask(CruiseResidentChecks::verify);
            advance(4);
        }else if(stage==4){
            System.out.println("[vandorlabs] cruise-resident PASS real prepared footprint and mounted client, raw queued-unload refusal, no chunks requested over wilderness, saved latest anchor");
            return true;
        }
        return false;
    }

    private static void advance(int next){stage=next;ticks=0;}

    private static void prepare(Minecraft client){
        require(!BlockAvailability.reanchorResidentCruise(),"default selects the departure anchor");
        File fixture=new File(client.mcDataDir,"config/cruise-resident-fixture.cfg");
        Configuration config=new Configuration(fixture);
        config.load();
        config.get("vehicles","reanchorResidentCruise",false).set(true);
        config.save();
        BlockAvailability.load(fixture);
        require(BlockAvailability.reanchorResidentCruise(),"real config selects resident-only reanchoring");
        pilot=client.getIntegratedServer().getPlayerList().getPlayers().get(0);
        world=pilot.getServerWorld();
        departure=pilot.posX;
        residentX=departure+FAR;
        wildernessX=residentX+FAR;
        originZ=pilot.posZ;
        int cx=chunk(residentX),cz=chunk(originZ);
        // Prepare genuine terrain before the no-load interval. The extra ring lets normal
        // vanilla population finish every required watcher column.
        for(int x=cx-7;x<=cx+7;x++)for(int z=cz-7;z<=cz+7;z++)world.getChunkProvider().provideChunk(x,z);
        world.getChunkProvider().saveChunks(true);
        world.getChunkProvider().flushToDisk();
        BlockPos seat=BlockPos.ORIGIN;
        craft=new EntityGroundVehicle(world);
        craft.install(new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null),
            new VehicleStructure.Cell(seat.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null)),seat,EnumFacing.NORTH,1));
        craft.setPosition(departure,255.9,originZ);
        world.spawnEntity(craft);
        pilot.setPositionAndUpdate(departure,256,originZ);
        craft.assignSeat(pilot,seat);
        require(pilot.startRiding(craft,true),"real local player boards the Pilot Seat");
        craft.hover=true;
        craft.hoverTarget=255.9;
    }

    private static void enter(){
        MinecraftForge.EVENT_BUS.register(AUDIT);
        require(!generated(wildernessX,originZ),"fresh destination absent before transit");
        NBTTagCompound savedFlight=new NBTTagCompound();
        savedFlight.setByte("MaxwellFlightPhase",(byte)3);
        craft.readFlightMode(savedFlight);
        craft.setPosition(departure,255.9,originZ);
        require(craft.cruise.move(0,1,0),"enter High Flight");
        require(craft.cruise.move(0,640,0),"raise enough to stay above the reentry path");
        // The guard must leave even the unload flag untouched. This direct check runs
        // at the production cadence with exactly the same real destination footprint.
        Chunk resident=world.getChunkProvider().getLoadedChunk(chunk(residentX),chunk(originZ));
        require(resident!=null&&resident.isTerrainPopulated(),"generated resident witness");
        int age=craft.ticksExisted;
        craft.ticksExisted=80;
        resident.unloadQueued=true;
        try{require(!CruiseResidentReanchor.ready(craft,residentX,craft.posY,originZ,craft.rotationYaw,craft.flightPitch(),craft.flightBank())&&resident.unloadQueued,
            "an unload-queued column is refused without canceling its unload");}
        finally{resident.unloadQueued=false;craft.ticksExisted=age;}
        require(craft.cruise.move(FAR,0,0),"advance towards the prepared resident site");
    }

    private static void crossWilderness(){
        require(craft.cruise.active()&&Math.abs(craft.posX-residentX)<.01
            && craft.chunkCoordX==chunk(residentX) && pilot.chunkCoordX==chunk(residentX)
            && Math.abs(pilot.managedPosX-residentX)<4,"actual mounted craft and watcher reindexed at the resident site");
        require(AUDIT.loads.isEmpty(),"resident hop loaded new terrain "+AUDIT.loads);
        AUDIT.hopped=true;
        require(craft.cruise.move(FAR,0,0),"continue logical route to fresh terrain");
    }

    private static void verify(){
        require(craft.cruise.active()&&Math.abs(craft.cruise.x()-wildernessX)<.01
            && Math.abs(craft.posX-residentX)<.01&&pilot.getRidingEntity()==craft,
            "unavailable terrain retains the last resident physical anchor");
        NBTTagCompound snapshot=craft.writeToNBT(new NBTTagCompound());
        EntityGroundVehicle written=new EntityGroundVehicle(world);
        written.readFromNBT(snapshot);
        require(written.cruise.active()&&written.cruise.x()==wildernessX&&written.posX==residentX
            && Math.abs(pilot.writeToNBT(new NBTTagCompound()).getTagList("Pos",6).getDoubleAt(0)-residentX)<4,
            "airframe and rider save at the actual resident anchor while geographic route is retained");
        checkpoint();
        require(idleSaves==2,"both saves were flushed while the craft held the same route X/Z");
        MinecraftForge.EVENT_BUS.unregister(AUDIT);
        require(AUDIT.loads.isEmpty()&&AUDIT.wildernessSaves.isEmpty(),"remote audit loads="+AUDIT.loads+" saves="+AUDIT.wildernessSaves);
        System.out.println("[vandorlabs][cruise-resident] departure="+departure+" resident="+residentX+
            " geographic="+wildernessX+" newRouteLoads=0 wildernessSaves=0 absentWitnesses=289 flushedIdleSaves="+idleSaves+
            " residentReloads="+AUDIT.residentReloads.size()+" firstResidentReload="+AUDIT.firstResidentReload);
    }

    private static void checkpoint(){
        require(craft.cruise.x()==wildernessX&&craft.posX==residentX,"idle checkpoint retains geographic and physical X");
        try{
            world.saveAllChunks(true,null);
            world.getChunkProvider().flushToDisk();
            for(int x=chunk(wildernessX)-8;x<=chunk(wildernessX)+8;x++)
                for(int z=chunk(originZ)-8;z<=chunk(originZ)+8;z++)
                    require(!world.getChunkProvider().isChunkGeneratedAt(x,z)&&!onDisk(x,z),"fresh route chunk appeared on disk or in the provider");
            idleSaves++;
        }catch(IOException|net.minecraft.world.MinecraftException failure){throw new IllegalStateException("Could not audit the saved route region",failure);}
    }

    private static boolean onDisk(int x,int z)throws IOException{
        File directory=((AnvilChunkLoader)world.getChunkProvider().chunkLoader).chunkSaveLocation;
        File file=new File(new File(directory,"region"),"r."+(x>>5)+"."+(z>>5)+".mca");
        if(!file.isFile())return false;
        try(RandomAccessFile data=new RandomAccessFile(file,"r")){
            data.seek(((z&31)*32+(x&31))*4L);
            return data.readInt()!=0;
        }
    }

    private static int chunk(double value){return (int)Math.floor(value/16);}
    private static boolean generated(double x,double z){return world.getChunkProvider().isChunkGeneratedAt(chunk(x),chunk(z));}

    @SideOnly(Side.CLIENT)
    public static final class Audit {
        final Set<ChunkPos> loads=new LinkedHashSet<>(), wildernessSaves=new LinkedHashSet<>();
        final Set<ChunkPos> residentReloads=new LinkedHashSet<>();
        boolean hopped;
        String firstResidentReload="none";
        private boolean remote(ChunkEvent event){
            return event.getWorld()==world&&event.getChunk().x*16.0>=departure+1024
                && event.getChunk().x*16.0<=wildernessX+1024&&Math.abs(event.getChunk().z*16.0-originZ)<=1024;
        }
        @SubscribeEvent public void load(ChunkEvent.Load event){
            if(!remote(event))return;
            ChunkPos at=event.getChunk().getPos();
            if(hopped&&Math.abs(at.x-chunk(residentX))<=7&&Math.abs(at.z-chunk(originZ))<=7&&wasOnDisk(at)){
                if(residentReloads.isEmpty()){
                    StringBuilder caller=new StringBuilder();
                    for(StackTraceElement frame:Thread.currentThread().getStackTrace())
                        if(frame.getClassName().startsWith("net.minecraft.")){
                            caller.append(frame.getClassName()).append('.').append(frame.getMethodName()).append(' ');
                            if(caller.length()>=450)break;
                        }
                    firstResidentReload=caller.toString().trim();
                }
                residentReloads.add(at);
            }else loads.add(at);
        }
        private boolean wasOnDisk(ChunkPos at){
            try{return onDisk(at.x,at.z);}
            catch(IOException failure){throw new IllegalStateException("Cannot verify previously resident terrain",failure);}
        }
        @SubscribeEvent public void save(ChunkDataEvent.Save event){
            if(remote(event)&&event.getChunk().x*16.0>=residentX+1024)wildernessSaves.add(event.getChunk().getPos());
        }
    }
}
