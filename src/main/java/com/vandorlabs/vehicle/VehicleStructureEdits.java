package com.vandorlabs.vehicle;

import net.minecraft.util.math.*;
import java.util.*;

/** Rebase an expanded immutable craft while preserving its world pose and assigned seats. */
public final class VehicleStructureEdits {
    private VehicleStructureEdits(){}
    static java.util.function.Consumer<String> testFault;
    private static void fault(String stage){java.util.function.Consumer<String> callback=testFault;if(callback!=null)callback.accept(stage);}
    private static void broadcast(EntityGroundVehicle craft,VehicleStructure snapshot){for(net.minecraft.entity.player.EntityPlayer player:craft.world.playerEntities)if(player instanceof net.minecraft.entity.player.EntityPlayerMP&&player.getDistanceSq(craft)<256*256)VehicleNetwork.send((net.minecraft.entity.player.EntityPlayerMP)player,snapshot,craft.getEntityId(),craft.getUniqueID(),new BlockPos(craft),false,false);}

    public static void install(EntityGroundVehicle craft,List<VehicleStructure.Cell> cells,String reason){install(craft,cells,reason,null);}
    /** Initial same-owner receipt only; no existing generation or General pose is rotated here. */
    static void installFirstCouplerScope(EntityGroundVehicle craft,List<VehicleStructure.Cell> cells,String reason,UUID generation){if(generation==null)throw new IllegalArgumentException("Chosen generation required");install(craft,cells,reason,generation);}
    private static void install(EntityGroundVehicle craft,List<VehicleStructure.Cell> cells,String reason,UUID firstGeneration){
        VehicleStructure before=craft.structure;if(firstGeneration!=null){if(craft.world.isRemote||craft.world.getMinecraftServer()==null||!craft.world.getMinecraftServer().isCallingFromMinecraftThread()||craft.isDead||craft.world.getEntityByID(craft.getEntityId())!=craft||!craft.couplerScopeReady()||craft.couplerAssociationGeneration()!=null||!craft.currentGeneralPose().bodies.isEmpty())throw new IllegalArgumentException("Initial unpaired ownership receipt is unavailable");before.requireCouplerScope(null);}
        try{
            int minX=0,minY=0,minZ=0;for(VehicleStructure.Cell cell:cells){minX=Math.min(minX,cell.pos.getX());minY=Math.min(minY,cell.pos.getY());minZ=Math.min(minZ,cell.pos.getZ());}
            BlockPos shift=new BlockPos(-minX,-minY,-minZ);List<VehicleStructure.Cell> rebased=new ArrayList<>();
            for(VehicleStructure.Cell cell:cells)rebased.add(new VehicleStructure.Cell(cell.pos.add(shift),cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),shift):null));
            VehicleStructure next=new VehicleStructure(rebased,craft.structure.seat.add(shift),craft.structure.facing,craft.structure.gearCount);
            VehicleWorld.CouplerScope chosenScope=firstGeneration==null?craft.currentCouplerScope():VehicleWorld.CouplerScope.external(craft.getUniqueID(),firstGeneration);VehicleWorld view=new VehicleWorld(craft.world,next,chosenScope);VehicleRotation.validate(next,view);next.encode();VehicleCollision collision=new VehicleCollision(view);
            double oldX=craft.structure.bounds.maxX/2,oldZ=craft.structure.bounds.maxZ/2,newX=next.bounds.maxX/2,newZ=next.bounds.maxZ/2;
            double angle=Math.toRadians(craft.rotationYaw),c=Math.cos(angle),s=Math.sin(angle),dx=newX-oldX-shift.getX(),dz=newZ-oldZ-shift.getZ();
            double x=craft.posX+oldX-newX+c*dx-s*dz,z=craft.posZ+oldZ-newZ+s*dx+c*dz,y=craft.posY-shift.getY();
            if(!collision.loaded(craft.world,x,y,z,0,0,0,craft.rotationYaw))throw new IllegalArgumentException(reason+" reaches unloaded terrain or world bounds");
            double oldPX=craft.posX,oldPY=craft.posY,oldPZ=craft.posZ,oldHover=craft.hoverTarget,altitudeShift=y-craft.posY;
            EntityGroundVehicle.CouplerScopeSnapshot scopeBefore=craft.captureCouplerAssociationScope();UUID publicationGeneration=firstGeneration==null?craft.couplerAssociationGeneration():firstGeneration;
            net.minecraft.nbt.NBTTagCompound ownershipBefore=new net.minecraft.nbt.NBTTagCompound();craft.writeCouplerOwnershipMetadata(ownershipBefore);
            net.minecraft.nbt.NBTTagCompound seatsBefore=craft.occupiedSeatAssignments();BlockPos taxiSeatBefore=craft.taxiReturnSeat;
            if(craft.structure!=before)throw new IllegalArgumentException("Source changed during chosen publication");
            if(firstGeneration!=null)craft.commitCouplerAssociationGeneration(null,firstGeneration);
            try{
                craft.install(next);fault("installed");craft.hoverTarget+=altitudeShift;craft.setPosition(x,y,z);fault("positioned");craft.signals.open();fault("signals");broadcast(craft,next);fault("broadcast");
            }catch(RuntimeException|LinkageError failure){
                try{craft.signals.close();}catch(RuntimeException|LinkageError close){failure.addSuppressed(close);}
                try{
                    craft.restoreCouplerAssociationScope(publicationGeneration,scopeBefore);craft.retainCouplerOwnershipMetadata(ownershipBefore);
                    craft.install(before);fault("rollback-installed");craft.hoverTarget=oldHover;craft.setPosition(oldPX,oldPY,oldPZ);
                    craft.restoreSeatAssignments(seatsBefore);craft.taxiReturnSeat=taxiSeatBefore;craft.restoreCouplerOwnershipMetadata(ownershipBefore);craft.signals.open();
                }catch(RuntimeException|LinkageError rollback){
                    failure.addSuppressed(rollback);try{craft.retainUnavailableSnapshot(before);}catch(RuntimeException|LinkageError quarantine){failure.addSuppressed(quarantine);}
                    if(java.util.Objects.equals(craft.couplerAssociationGeneration(),publicationGeneration))craft.restoreCouplerAssociationScope(publicationGeneration,scopeBefore);
                    craft.retainCouplerOwnershipMetadata(ownershipBefore);craft.hoverTarget=oldHover;craft.setPosition(oldPX,oldPY,oldPZ);craft.restoreSeatAssignments(seatsBefore);craft.taxiReturnSeat=taxiSeatBefore;
                }
                if(craft.structure==before)try{broadcast(craft,before);}catch(RuntimeException|LinkageError notification){failure.addSuppressed(notification);}
                throw new IllegalArgumentException(reason+" publication failed; original snapshot retained",failure);
            }
        }catch(java.io.IOException error){throw new IllegalArgumentException(reason+" cannot be saved",error);}catch(LinkageError unsupported){if(firstGeneration!=null)throw new IllegalArgumentException(reason+" unsupported; original snapshot retained",unsupported);throw unsupported;}
    }
}
