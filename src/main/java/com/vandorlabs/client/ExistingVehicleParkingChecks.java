package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;
import java.util.concurrent.Future;

/** Explicit disposable-world-copy parking acceptance, never enabled in normal play. */
final class ExistingVehicleParkingChecks {
    private static Future<?> pending;
    private static final List<BlockPos> controllers=new ArrayList<>();
    private static boolean started,finished;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("existing vehicle parking: "+text);}
    static boolean tick(Minecraft mc,int ticks)throws Exception {
        if(!started&&ticks>=40){
            started=true;
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                try {
                    WorldServer world=mc.getIntegratedServer().getWorld(0);
                    EntityPlayerMP player=world.getMinecraftServer().getPlayerList().getPlayers().get(0);
                    List<EntityGroundVehicle> vehicles=new ArrayList<>();
                    for(Entity entity:world.loadedEntityList)if(entity instanceof EntityGroundVehicle&&!entity.isDead)vehicles.add((EntityGroundVehicle)entity);
                    require(!vehicles.isEmpty(),"saved vehicle loaded");
                    java.lang.reflect.Method validate=VehicleService.class.getDeclaredMethod("validateParking",EntityPlayerMP.class,EntityGroundVehicle.class,BlockPos.class,boolean.class,boolean.class);
                    validate.setAccessible(true);
                    for(EntityGroundVehicle craft:vehicles){
                        craft.stopForParking();require(!craft.hover,"saved craft is in Taxi");
                        VehicleRotation.Plan plan=VehicleRotation.plan(craft);
                        validate.invoke(null,player,craft,plan.origin,false,false);
                        for(VehicleStructure.Cell cell:plan.structure.cells)if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockRampController)controllers.add(plan.origin.add(cell.pos));
                        VehicleTransfer.park(world,craft,plan.origin,player,false);
                        require(craft.isDead,"saved vehicle parked successfully");
                    }
                }catch(Exception failure){throw new IllegalStateException("saved vehicle parking failed",failure);}
            });
        }
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;}
        if(started&&!finished&&ticks>=160){
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);
                for(BlockPos pos:controllers){
                    require(world.getTileEntity(pos) instanceof TileEntityRampController,"parked ramp controller remains installed");
                    TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(pos);
                    require(!ramp.error,"parked ramp has no recovery error: "+ramp.status);
                    for(BlockPos cell:ramp.vehicleCells())require(world.getTileEntity(cell) instanceof TileEntityControlledRamp&&ramp.owns((TileEntityControlledRamp)world.getTileEntity(cell)),"parked ramp section retains owner");
                }
                require(!VehicleTransfer.pending(world),"parking journal retired");
                System.out.println("[vandorlabs][reprolab] existing-vehicle-parking PASS saved-craft full-parking-validation real-transfer normal-world-ticks ramp-ownership journal-retired");
            });
            finished=true;return false;
        }
        return finished&&pending==null;
    }
}
