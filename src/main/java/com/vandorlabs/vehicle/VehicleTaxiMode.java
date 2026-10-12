package com.vandorlabs.vehicle;

import com.vandorlabs.tiles.TileEntityPilotSeat;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

/** Taxi Mode belongs to the saved seat, including legacy seats without the field. */
public final class VehicleTaxiMode {
    private VehicleTaxiMode(){}
    public static boolean enabled(VehicleStructure structure) {
        if(structure==null)return false;
        NBTTagCompound data=structure.at(structure.seat).tileData();
        return data==null || !data.hasKey("TaxiMode",1) || data.getBoolean("TaxiMode");
    }
    public static VehicleStructure withSetting(VehicleStructure structure,boolean enabled) {
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:structure.cells) {
            if(!cell.pos.equals(structure.seat)){cells.add(cell);continue;}
            NBTTagCompound data=cell.tileData();
            if(data==null){TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(cell.pos);data=seat.writeToNBT(new NBTTagCompound());}
            data.setBoolean("TaxiMode",enabled);cells.add(new VehicleStructure.Cell(cell.pos,cell.state,data));
        }
        return new VehicleStructure(cells,structure.seat,structure.facing,structure.gearCount);
    }
    public static void apply(EntityGroundVehicle craft,boolean enabled) {
        craft.install(withSetting(craft.structure,enabled));
        if(craft.view.getTileEntity(craft.structure.seat) instanceof TileEntityPilotSeat)
            ((TileEntityPilotSeat)craft.view.getTileEntity(craft.structure.seat)).setTaxiMode(enabled);
    }
}
