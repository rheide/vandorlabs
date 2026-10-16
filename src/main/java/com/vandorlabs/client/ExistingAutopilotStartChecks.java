package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.concurrent.Future;

/** Opt-in reproduction in a copied world; never attaches to the player's live server. */
final class ExistingAutopilotStartChecks {
    private static final BlockPos COMPUTER=new BlockPos(-104,85,161),PILOT=new BlockPos(-103,84,161);
    private static Future<?> pending;private static int stage,frames;private static boolean done;
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException("existing autopilot start: "+reason);}
    static boolean tick(Minecraft mc)throws Exception{
        if(done)return true;
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;stage++;frames=0;}
        if(stage==0){
            if(!(mc.world.getTileEntity(COMPUTER) instanceof TileEntityAutopilot))return false;
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                actor.dismountRidingEntity();
                for(BlockPos pos:new BlockPos[]{PILOT,PILOT.west()}){TileEntity tile=world.getTileEntity(pos);require(tile instanceof TileEntityPilotSeat,"saved seat/controller exists");((TileEntityPilotSeat)tile).owner=actor.getUniqueID();tile.markDirty();}
                TileEntityAutopilot tile=(TileEntityAutopilot)world.getTileEntity(COMPUTER);tile.setEnabled(false);tile.pulseDeparture();
            });return false;
        }
        if(stage==1&&++frames>=40){
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                TileEntityAutopilot tile=(TileEntityAutopilot)world.getTileEntity(COMPUTER);require(tile!=null&&tile.status.contains("No route marker below Autopilot"),"off-route saved craft is explained: "+(tile==null?"missing":tile.status));
                tile.setEnabled(false);
                try{
                    VehicleScan scan=new VehicleScan(world,PILOT);for(int i=0;i<200&&!scan.step(256);i++);require(scan.error==null&&scan.result!=null,"saved craft scan: "+scan.error);
                    EntityGroundVehicle craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);
                    // Align only this isolated copy's Autopilot with its existing iron marker row.
                    craft.setPosition(craft.posX,craft.posY,craft.posZ-1);VehicleTransfer.park(world,craft,scan.origin.north(),actor);
                    TileEntityAutopilot aligned=(TileEntityAutopilot)world.getTileEntity(COMPUTER.north());require(aligned!=null,"copied craft moved one block onto its route");aligned.pulseDeparture();
                }catch(Exception failure){throw new IllegalStateException(failure);}
            });return false;
        }
        if(stage==2&&++frames>=80){
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);EntityGroundVehicle found=null;
                for(EntityGroundVehicle craft:VehicleLookup.loaded(world))if(!craft.isDead&&craft.posX>-115&&craft.posX< -85&&craft.posZ>145&&craft.posZ<175)found=craft;
                require(found!=null&&found.autopilot.enabled(),"outside activation assembles and enables saved craft");
                require(found.getPassengers().isEmpty()&&found.posX> -104,"saved craft advances east without a seated pilot: "+found.posX+" status="+found.autopilot.controller().status);
                System.out.println("[vandorlabs][reprolab] existing-autopilot-start PASS saved-craft off-route-diagnostic aligned-copy unoccupied-activation protected-queued-assembly actual-eastward-travel");
            });return false;
        }
        if(stage==3){done=true;return true;}return false;
    }
    private ExistingAutopilotStartChecks(){}
}
