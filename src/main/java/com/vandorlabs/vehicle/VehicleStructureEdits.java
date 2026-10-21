package com.vandorlabs.vehicle;

import net.minecraft.util.math.*;
import java.util.*;

/** Rebase an expanded immutable craft while preserving its world pose and assigned seats. */
public final class VehicleStructureEdits {
    private VehicleStructureEdits(){}
    public static void install(EntityGroundVehicle craft,List<VehicleStructure.Cell> cells,String reason){
        try{
            int minX=0,minY=0,minZ=0;for(VehicleStructure.Cell cell:cells){minX=Math.min(minX,cell.pos.getX());minY=Math.min(minY,cell.pos.getY());minZ=Math.min(minZ,cell.pos.getZ());}
            BlockPos shift=new BlockPos(-minX,-minY,-minZ);List<VehicleStructure.Cell> rebased=new ArrayList<>();
            for(VehicleStructure.Cell cell:cells)rebased.add(new VehicleStructure.Cell(cell.pos.add(shift),cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),shift):null));
            VehicleStructure next=new VehicleStructure(rebased,craft.structure.seat.add(shift),craft.structure.facing,craft.structure.gearCount);
            VehicleWorld view=new VehicleWorld(craft.world,next);VehicleRotation.validate(next,view);next.encode();VehicleCollision collision=new VehicleCollision(view);
            double oldX=craft.structure.bounds.maxX/2,oldZ=craft.structure.bounds.maxZ/2,newX=next.bounds.maxX/2,newZ=next.bounds.maxZ/2;
            double angle=Math.toRadians(craft.rotationYaw),c=Math.cos(angle),s=Math.sin(angle),dx=newX-oldX-shift.getX(),dz=newZ-oldZ-shift.getZ();
            double x=craft.posX+oldX-newX+c*dx-s*dz,z=craft.posZ+oldZ-newZ+s*dx+c*dz,y=craft.posY-shift.getY();
            if(!collision.loaded(craft.world,x,y,z,0,0,0,craft.rotationYaw))throw new IllegalArgumentException(reason+" reaches unloaded terrain or world bounds");
            double altitudeShift=y-craft.posY;craft.install(next);craft.hoverTarget+=altitudeShift;craft.setPosition(x,y,z);craft.signals.open();
            for(net.minecraft.entity.player.EntityPlayer player:craft.world.playerEntities)if(player instanceof net.minecraft.entity.player.EntityPlayerMP&&player.getDistanceSq(craft)<256*256)VehicleNetwork.send((net.minecraft.entity.player.EntityPlayerMP)player,next,craft.getEntityId(),craft.getUniqueID(),new BlockPos(craft),false,false);
        }catch(java.io.IOException error){throw new IllegalArgumentException(reason+" cannot be saved",error);}
    }
}
