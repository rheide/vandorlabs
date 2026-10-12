package com.vandorlabs.vehicle;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Replace only authoritative seat data; channel editing does not rebuild hull geometry. */
public final class VehicleSeatSettings {
    private VehicleSeatSettings(){}
    public static VehicleStructure replace(VehicleStructure structure,NBTTagCompound data,BlockPos translation) {
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:structure.cells)cells.add(cell.pos.equals(structure.seat)?
                new VehicleStructure.Cell(cell.pos,cell.state,VehicleTileData.translated(data,translation)):cell);
        return new VehicleStructure(cells,structure.seat,structure.facing,structure.gearCount);
    }
}
