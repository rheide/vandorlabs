package com.vandorlabs.vehicle;

import com.vandorlabs.cockpit.CockpitReservations;
import com.vandorlabs.tiles.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import java.util.*;

/** Rare seat-extension edits use the same bounded snapshot publication as vehicle ramps. */
public final class VehicleCockpits {
    private VehicleCockpits(){}
    public static void refreshGeometry(EntityGroundVehicle craft){craft.collisionCache.clear();craft.collision=new VehicleCollision(craft.view);craft.setPosition(craft.posX,craft.posY,craft.posZ);for(net.minecraft.entity.Entity rider:craft.getPassengers())craft.updatePassenger(rider);}
    public static void reserve(EntityGroundVehicle craft,TileEntityCockpit tile){
        List<VehicleStructure.Cell> cells=new ArrayList<>();boolean added=false;
        for(VehicleStructure.Cell cell:craft.structure.cells)cells.add(cell.pos.equals(tile.getPos())?new VehicleStructure.Cell(cell.pos,cell.state,tile.writeToNBT(new NBTTagCompound())):cell);
        for(BlockPos p:CockpitReservations.rear(tile,tile.facing()))if(craft.structure.at(p)==null){TileEntityCockpitPart part=new TileEntityCockpitPart();part.setPos(p);part.anchor=tile.getPos();cells.add(new VehicleStructure.Cell(p,net.minecraft.block.Block.getBlockFromName("vandorlabs:cockpit_part").getDefaultState(),part.writeToNBT(new NBTTagCompound())));added=true;}
        if(!added){refreshGeometry(craft);return;}
        VehicleStructureEdits.install(craft,cells,"Seat movement");
    }
}
