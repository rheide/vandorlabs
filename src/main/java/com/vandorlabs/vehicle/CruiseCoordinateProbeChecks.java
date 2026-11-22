package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.ModBlocks;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Connected-player comparison of anchored transit, native relocation and the coordinate projection experiment. */
@SideOnly(Side.CLIENT)
final class CruiseCoordinateProbeChecks {
    private static final String MODE=System.getProperty("vandorlabs.cruiseCoordinateProbe","");
    private static final int FAR=8192, SWEEP=768, STEP=12;
    private static final Audit AUDIT=new Audit();
    private static EntityGroundVehicle craft;
    private static EntityPlayerMP pilot;
    private static WorldServer world;
    private static Future<?> work;
    private static int stage, wait, steps, startingTicks;
    private static double startX,startZ,remoteX;

    private CruiseCoordinateProbeChecks() {}
    static boolean enabled(){return !MODE.isEmpty();}
    private static boolean relocation(){return "relocation".equals(MODE);}
    private static boolean projection(){return "projection".equals(MODE);}
    private static void require(boolean valid,String reason){
        if(!valid)throw new IllegalStateException("Cruise coordinate probe "+MODE+" stage="+stage+" step="+steps+": "+reason);
    }

    static boolean tick(Minecraft mc)throws Exception {
        require(++wait<680,"timed out rider="+mc.player.getRidingEntity()+" x="+mc.player.posX);
        if(work!=null){
            if(!work.isDone())return false;
            work.get();
            work=null;
        }
        if(stage==0){
            require(relocation()||projection()||"anchor".equals(MODE),"unknown probe mode");
            mc.displayGuiScreen(null);
            work=mc.getIntegratedServer().addScheduledTask(()->place(mc));
            advance(1);
        }else if(stage==1&&wait>15&&mc.player.getRidingEntity() instanceof EntityGroundVehicle){
            work=mc.getIntegratedServer().addScheduledTask(CruiseCoordinateProbeChecks::start);
            advance(2);
        }else if(stage==2&&wait>=5){
            if(relocation()){
                // Give stock player chunk watching and the generator time to publish its first remote batch.
                if(wait>=30){work=mc.getIntegratedServer().addScheduledTask(CruiseCoordinateProbeChecks::finish);advance(3);}
            }else if(steps<SWEEP/STEP){
                work=mc.getIntegratedServer().addScheduledTask(CruiseCoordinateProbeChecks::step);
            }else{
                require(mc.player.getRidingEntity() instanceof EntityGroundVehicle,"real client stayed mounted");
                require(Math.abs(mc.player.posX-(projection()?remoteX+SWEEP:startX))<20,
                    "unexpected actual client coordinate "+mc.player.posX);
                System.out.println("[vandorlabs][coordinate-probe] client playerX="+mc.player.posX+
                    " craftX="+mc.player.getRidingEntity().posX+" playerChunk="+mc.player.chunkCoordX);
                work=mc.getIntegratedServer().addScheduledTask(CruiseCoordinateProbeChecks::finish);
                advance(3);
            }
        }else if(stage==3){
            System.out.println("[vandorlabs] cruise-coordinate-probe "+MODE+" PASS");
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
        craft.install(new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null),
            new VehicleStructure.Cell(seat.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null)),seat,EnumFacing.NORTH,1));
        startX=pilot.posX;
        startZ=pilot.posZ;
        remoteX=startX+FAR;
        craft.setPosition(startX,255.9,startZ);
        world.spawnEntity(craft);
        pilot.setPositionAndUpdate(startX,256,startZ);
        craft.assignSeat(pilot,seat);
        require(pilot.startRiding(craft,true),"real pilot mounts");
        craft.hover=true;
        craft.hoverTarget=255.9;
    }

    private static void start(){
        require(!generated(remoteX,startZ)&&!generated(remoteX+SWEEP,startZ),"remote witness columns start without terrain");
        MinecraftForge.EVENT_BUS.register(AUDIT);
        NBTTagCompound restoredFlight=new NBTTagCompound();
        restoredFlight.setByte("MaxwellFlightPhase",(byte)3);
        craft.readFlightMode(restoredFlight);
        craft.setPosition(startX,255.9,startZ);
        require(craft.cruise.move(0,1,0),"enter high flight");
        require(craft.cruise.move(FAR,640,0),"advance geographic route");
        startingTicks=craft.ticksExisted;
        if(relocation()){
            craft.setPosition(remoteX,craft.posY,startZ);
            craft.updatePassenger(pilot);
            pilot.connection.setPlayerLocation(pilot.posX,pilot.posY,pilot.posZ,pilot.rotationYaw,pilot.rotationPitch);
        }
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
        require(Math.abs(craft.posX-startX)<.001,"craft stays in its departure storage column");
        require(Math.abs(pilot.posX-(projection()?craft.cruise.x():startX))<3,"unexpected actual server player x="+pilot.posX);
        require(Math.abs(pilot.managedPosX-startX)<3 && Math.abs(pilot.chunkCoordX-(int)Math.floor(startX/16))<=1,
            "server player remains in approved departure tracking");
    }

    private static void finish(){
        MinecraftForge.EVENT_BUS.unregister(AUDIT);
        boolean loaded=generated(remoteX,startZ);
        if(relocation()){
            require(AUDIT.loads.size()>0&&loaded,"stock relocation automatically requested remote terrain");
        }else{
            require(craft.ticksExisted>startingTicks+20,"craft really ticks during transit");
            require(pilot.getRidingEntity()==craft,"server pilot remains mounted");
            require(AUDIT.loads.isEmpty()&&AUDIT.saves.isEmpty()&&!loaded,
                "route terrain was requested: loads="+AUDIT.loads.size()+" saves="+AUDIT.saves.size()+" first="+AUDIT.firstLoad);
            for(int offset=0;offset<=SWEEP;offset+=16)for(int dz=-96;dz<=96;dz+=16)
                require(!generated(remoteX+offset,startZ+dz),"unexpected disk or loaded chunk at "+offset+","+dz);
        }
        System.out.println("[vandorlabs][coordinate-probe] mode="+MODE+" remoteLoads="+AUDIT.loads.size()+
            " remoteSaves="+AUDIT.saves.size()+" generatedWitness="+loaded+" playerX="+pilot.posX+
            " managedX="+pilot.managedPosX+" craftX="+craft.posX+" craftTicks="+(craft.ticksExisted-startingTicks)+
            " savedPlayerX="+pilot.writeToNBT(new NBTTagCompound()).getTagList("Pos",6).getDoubleAt(0)+
            (AUDIT.firstLoad==null?"":" firstLoad="+AUDIT.firstLoad));
    }

    private static boolean generated(double x,double z){
        return world.getChunkProvider().isChunkGeneratedAt((int)Math.floor(x/16),(int)Math.floor(z/16));
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
