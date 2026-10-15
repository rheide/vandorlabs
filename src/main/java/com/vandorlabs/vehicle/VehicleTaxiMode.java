package com.vandorlabs.vehicle;

import com.vandorlabs.tiles.TileEntityPilotSeat;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

/** Taxi Mode belongs to the controller, with a saved-seat fallback for old captured craft. */
public final class VehicleTaxiMode {
    private VehicleTaxiMode(){}
    public static boolean enabled(VehicleStructure structure) {
        if(structure==null)return false;
        structure=VehicleControlSettings.migrate(structure);
        NBTTagCompound data=structure.at(VehicleControlSettings.position(structure)).tileData();
        return data==null || !data.hasKey("TaxiMode",1) || data.getBoolean("TaxiMode");
    }
    public static VehicleStructure withSetting(VehicleStructure structure,boolean enabled) {
        structure=VehicleControlSettings.migrate(structure);
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:structure.cells) {
            if(!cell.pos.equals(VehicleControlSettings.position(structure))){cells.add(cell);continue;}
            NBTTagCompound data=cell.tileData();
            if(data==null){TileEntityPilotSeat seat=structure.controller()==null?new TileEntityPilotSeat():new com.vandorlabs.tiles.TileEntityVehicleController();seat.setPos(cell.pos);data=seat.writeToNBT(new NBTTagCompound());}
            data.setBoolean("TaxiMode",enabled);if(structure.controller()!=null)data.setBoolean("VehicleControllerControls",true);cells.add(new VehicleStructure.Cell(cell.pos,cell.state,data));
        }
        return new VehicleStructure(cells,structure.seat,structure.facing,structure.gearCount);
    }
    public static void apply(EntityGroundVehicle craft,boolean enabled) {
        craft.install(withSetting(craft.structure,enabled));
        if(craft.view.getTileEntity(VehicleControlSettings.position(craft.structure)) instanceof TileEntityPilotSeat)
            ((TileEntityPilotSeat)craft.view.getTileEntity(VehicleControlSettings.position(craft.structure))).setTaxiMode(enabled);
    }
}
