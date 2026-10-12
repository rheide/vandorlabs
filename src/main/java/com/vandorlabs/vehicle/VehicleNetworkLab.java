package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** Opt-in dedicated-server regression. No work or interception in ordinary games. */
@Mod.EventBusSubscriber(modid=com.vandorlabs.VandorLabs.MODID)
public final class VehicleNetworkLab {
    public static final boolean ENABLED=Boolean.getBoolean("vandorlabs.vehicleNetworkLab");
    private static final int[] DELAYS={0,50,100,200};
    private static final List<Delivery> deliveries=new ArrayList<>();
    private static final Random random=new Random(719);
    private static EntityGroundVehicle craft;
    private static int ticks,inputs,states;
    private static final class Delivery {final long at;final Runnable action;Delivery(long at,Runnable action){this.at=at;this.action=action;}}
    public static int phase(){return craft==null?0:Math.min(4,ticks/240);}
    public static int elapsed(){return ticks%240;}
    private static void delay(Runnable action){int ms=DELAYS[Math.min(3,phase())];deliveries.add(new Delivery(System.nanoTime()+Math.max(0,ms+(ms==0?0:random.nextInt(41)-20))*1000000L,action));}
    public static boolean input(EntityPlayerMP player,VehicleNetwork.Packet packet,EntityGroundVehicle vehicle) {
        if(!ENABLED)return false;
        if(++inputs%19!=0)delay(()->vehicle.input(player,packet.sequence,packet.forward,packet.strafe,packet.flag));
        return true;
    }
    public static boolean state(VehicleNetwork.Packet packet,EntityGroundVehicle vehicle) {
        if(!ENABLED)return false;
        packet.total=phase();packet.offset=elapsed();
        if(++states%17!=0)delay(()->{
            PacketHandler.INSTANCE.sendToAllTracking(packet,vehicle);
            if(vehicle.getControllingPassenger() instanceof EntityPlayerMP)PacketHandler.INSTANCE.sendTo(packet,(EntityPlayerMP)vehicle.getControllingPassenger());
        });
        return true;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(!ENABLED || event.phase!=TickEvent.Phase.END)return;
        net.minecraft.server.MinecraftServer server=net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        Iterator<Delivery> ready=deliveries.iterator();List<Runnable> work=new ArrayList<>();long now=System.nanoTime();
        while(ready.hasNext()){Delivery d=ready.next();if(now>=d.at){work.add(d.action);ready.remove();}}for(Runnable action:work)action.run();
        EntityPlayerMP driver=server.getPlayerList().getPlayerByUsername("VDriver"),observer=server.getPlayerList().getPlayerByUsername("VObserver");
        if(driver==null || observer==null)return;
        WorldServer world=server.getWorld(0);
        if(craft==null) {
            world.getGameRules().setOrCreateGameRule("doMobSpawning","false");world.setWorldTime(6000);
            for(BlockPos at:BlockPos.getAllInBox(new BlockPos(-48,100,-48),new BlockPos(48,100,48)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
            List<VehicleStructure.Cell> cells=new ArrayList<>();
            for(int x=0;x<12;x++)for(int z=0;z<5;z++)cells.add(new VehicleStructure.Cell(new BlockPos(x,0,z),Blocks.STONE.getDefaultState(),null));
            BlockPos seat=new BlockPos(11,1,2);cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
            cells.add(new VehicleStructure.Cell(seat.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),null));
            BlockPos door=new BlockPos(0,1,2);
            cells.add(new VehicleStructure.Cell(door,Blocks.OAK_DOOR.getDefaultState(),null));
            cells.add(new VehicleStructure.Cell(door.up(),Blocks.OAK_DOOR.getDefaultState().withProperty(net.minecraft.block.BlockDoor.HALF,net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER),null));
            craft=new EntityGroundVehicle(world);craft.install(VehicleTaxiMode.withSetting(new VehicleStructure(cells,seat,EnumFacing.NORTH,1),false));craft.setPosition(-6,101,-2);
            if(!world.spawnEntity(craft))throw new IllegalStateException("dedicated craft spawn failed");
            driver.setGameType(net.minecraft.world.GameType.CREATIVE);observer.setGameType(net.minecraft.world.GameType.SPECTATOR);
            driver.setPositionAndUpdate(5,103,0);observer.connection.setPlayerLocation(0,108,12,180,25);craft.board(driver);
        }
        ticks++;
        if(ticks%60==0)VehicleDoors.toggle(craft,new BlockPos(0,1,2));
        if(ticks==960)System.out.println("[vandorlabs] vehicle-controller "+craft.diagnostics.summary());
        if(ticks%240==0)System.out.println("[vandorlabs] vehicle-network phase="+phase()+" inputs="+inputs+" state="+states);
    }
}
