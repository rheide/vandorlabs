package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockPilotSeat;
import com.vandorlabs.items.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.*;
import java.util.*;

/** Actual surface reach, independent of vanilla's entity-origin interaction gate. */
public final class VehicleInteraction {
    private VehicleInteraction() {}
    private static final Map<EntityPlayerMP,Integer> lastClick = new WeakHashMap<>();
    public static final class Target {
        public final EntityGroundVehicle craft;
        public final VehicleSelection.Hit hit;
        private Target(EntityGroundVehicle craft, VehicleSelection.Hit hit) { this.craft=craft; this.hit=hit; }
    }
    public static Target pointed(EntityPlayer player) {
        double reach=Math.min(8,player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue());
        if(!Double.isFinite(reach) || reach<=0)return null;
        Vec3d start=player.getPositionEyes(1),end=start.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(reach));
        RayTraceResult terrain=player.world.rayTraceBlocks(start,end,false,true,false);
        double nearest=terrain==null?reach*reach:start.squareDistanceTo(terrain.hitVec);
        Target target=null;
        for(EntityGroundVehicle craft:VehicleLookup.near(player.world,new AxisAlignedBB(start.x,start.y,start.z,end.x,end.y,end.z).grow(.001))) {
            VehicleSelection.Hit hit=VehicleSelection.local(craft.structure,craft.view,craft.toLocal(start),craft.toLocal(end),nearest,player.getRidingEntity()==craft?craft.seatForPassenger(player):null);
            if(hit!=null){nearest=hit.distanceSq;target=new Target(craft,hit);}
        }
        if(target==null)return null;
        // Other ordinary entities still occlude an onboard target. Craft discovery
        // above intentionally does not depend on vanilla's origin-section index.
        for(Entity entity:player.world.getEntitiesInAABBexcluding(player,new AxisAlignedBB(start,end).grow(1),e->true)) {
            if(entity instanceof EntityGroundVehicle || entity==player.getRidingEntity() || !entity.canBeCollidedWith())continue;
            AxisAlignedBB box=entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
            RayTraceResult hit=box.calculateIntercept(start,end);
            if(box.contains(start) || hit!=null && start.squareDistanceTo(hit.hitVec)<=nearest)return null;
        }
        return target;
    }
    public static boolean supported(EntityPlayer player,Target target) {
        return target!=null && (player.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER
                || target.hit.action==VehicleSelection.Action.CANOPY
                || target.hit.action==VehicleSelection.Action.DOOR
                || target.hit.action==VehicleSelection.Action.SCREEN && !player.isSneaking() && VehicleSignals.supports(target.craft.view,target.hit.cell.pos)
                || target.hit.action==VehicleSelection.Action.CONTROLLER
                || target.hit.action==VehicleSelection.Action.BOARD);
    }
    public static void receive(EntityPlayerMP player,VehicleNetwork.Packet packet) {
        if(player.isDead || player.isSpectator() || packet.type!=VehicleNetwork.INTERACT)return;
        Integer before=lastClick.get(player);
        if(before!=null && before==player.ticksExisted)return;
        lastClick.put(player,player.ticksExisted);
        Entity raw=player.world.getEntityByID(packet.entity);
        if(!(raw instanceof EntityGroundVehicle) || raw.isDead || !raw.getUniqueID().equals(packet.token))return;
        Target target=pointed(player);
        if(target==null || target.craft!=raw || !target.hit.cell.pos.equals(packet.origin))return;
        if(!supported(player,target) || target.craft.ownershipBlocked || target.craft.structure==null)return;
        // Keep Forge interaction cancellation and held-item semantics after our
        // surface reach check, without vanilla's unrelated entity-origin gate.
        player.interactOn(target.craft,EnumHand.MAIN_HAND);
    }
    public static boolean activate(EntityPlayer player,Target target) {
        if(player.isDead || player.isSpectator() || !supported(player,target))return false;
        EntityGroundVehicle craft=target.craft;
        if(craft.isDead || craft.world!=player.world || craft.structure==null || craft.ownershipBlocked)return false;
        if(player.world.isRemote)return true;
        EntityPlayerMP server=(EntityPlayerMP)player;
        if(player.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER) {
            if(target.hit.action==VehicleSelection.Action.BOARD){BlockPos seat=target.hit.cell.state.getValue(BlockPilotSeat.UPPER)?target.hit.cell.pos.down():target.hit.cell.pos;VehicleService.previewSeat(server,craft,seat);}
            else if(target.hit.action==VehicleSelection.Action.CONTROLLER)VehicleService.previewController(server,craft,target.hit.cell.pos);
            else VehicleService.previewParking(server,craft);return true;
        }
        if(target.hit.action==VehicleSelection.Action.CONTROLLER){VehicleService.previewController(server,craft,target.hit.cell.pos);return true;}
        if(target.hit.action==VehicleSelection.Action.CANOPY) {
            if(!VehicleCanopies.toggle(craft,target.hit.cell.pos))VehicleNetwork.error(server,"This canopy has an incomplete captured group.");
            return true;
        }
        if(target.hit.action==VehicleSelection.Action.DOOR) {
            if(!VehicleDoors.toggle(craft,target.hit.cell.pos))VehicleNetwork.error(server,"This door has an incomplete captured group.");
            return true;
        }
        if(target.hit.action==VehicleSelection.Action.SCREEN)return craft.signals.click(target.hit.cell.pos,player);
        BlockPos seat=target.hit.cell.pos;
        if(target.hit.cell.state.getValue(BlockPilotSeat.UPPER))seat=seat.down();
        if(!craft.structure.pilotSeats().contains(seat))return false;
        if(player.isSneaking()){VehicleService.previewSeat(server,craft,seat);return true;}
        if(!craft.board(player,seat))VehicleNetwork.error(server,"The Pilot Seat is occupied or cannot be boarded.");
        return true;
    }
}
