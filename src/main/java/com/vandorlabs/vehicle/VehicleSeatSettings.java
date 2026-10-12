package com.vandorlabs.vehicle;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Replace only authoritative seat data; channel editing does not rebuild hull geometry. */
public final class VehicleSeatSettings {
    private VehicleSeatSettings(){}
    /** Seat edits need not reject unrelated machines changing inventory/burn time. */
    public static boolean matchesPlaced(VehicleStructure structure,net.minecraft.world.World world,BlockPos origin) {
        VehicleStructure.Cell saved=structure.at(structure.seat);BlockPos at=origin.add(structure.seat);
        if(saved==null || !world.isBlockLoaded(at) || world.getBlockState(at)!=saved.state
                || !(world.getTileEntity(at) instanceof com.vandorlabs.tiles.TileEntityPilotSeat) || saved.tileData()==null)return false;
        NBTTagCompound actual=VehicleTileData.translated(world.getTileEntity(at).writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin));
        NBTTagCompound expected=saved.tileData();actual.removeTag("VehicleSignalHigh");expected.removeTag("VehicleSignalHigh");
        return actual.equals(expected);
    }
    public static VehicleStructure replace(VehicleStructure structure,NBTTagCompound data,BlockPos translation) {
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:structure.cells)cells.add(cell.pos.equals(structure.seat)?
                new VehicleStructure.Cell(cell.pos,cell.state,VehicleTileData.translated(data,translation)):cell);
        return new VehicleStructure(cells,structure.seat,structure.facing,structure.gearCount);
    }
}
