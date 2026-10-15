package com.vandorlabs.vehicle;

import com.vandorlabs.tiles.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Controller ownership with a canonical-seat fallback only for controller-less legacy craft. */
public final class VehicleControlSettings {
    private VehicleControlSettings(){}
    public static BlockPos position(VehicleStructure structure){VehicleStructure.Cell controller=structure.controller();return controller==null?structure.seat:controller.pos;}
    public static TileEntityPilotSeat tile(EntityGroundVehicle craft){net.minecraft.tileentity.TileEntity tile=craft.view.getTileEntity(position(craft.structure));return tile instanceof TileEntityPilotSeat?(TileEntityPilotSeat)tile:null;}
    public static VehicleStructure migrate(VehicleStructure structure){
        VehicleStructure.Cell controller=structure.controller();if(controller==null)return structure;
        NBTTagCompound data=controller.tileData();if(data==null){TileEntityVehicleController tile=new TileEntityVehicleController();tile.setPos(controller.pos);data=tile.writeToNBT(new NBTTagCompound());}
        if(data.getBoolean("VehicleControllerControls"))return structure;
        NBTTagCompound legacy=structure.at(structure.seat).tileData();
        if(legacy!=null){
            for(String key:new String[]{"RedstoneChannels","TaxiMode","PilotRedstoneMode","VehicleSignalHigh","VehicleModeChannels0","VehicleModeChannels1","VehicleModeChannels2"})
                if(legacy.hasKey(key))data.setTag(key,legacy.getTag(key).copy());
            if(!data.hasUniqueId("VehicleOwner")&&legacy.hasUniqueId("VehicleOwner"))data.setUniqueId("VehicleOwner",legacy.getUniqueId("VehicleOwner"));
        }
        data.setBoolean("VehicleControllerControls",true);
        return VehicleSeatSettings.replaceAt(structure,controller.pos,data,BlockPos.ORIGIN);
    }
    /** Migration touches only the identified live controller, preserving every other source cell. */
    public static VehicleStructure migratePlaced(World world,BlockPos origin,VehicleStructure structure){
        VehicleStructure.Cell controller=structure.controller();if(controller==null)return structure;
        BlockPos at=origin.add(controller.pos);if(!world.isBlockLoaded(at))return structure;
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(at);
        if(!(raw instanceof TileEntityVehicleController)||world.getBlockState(at)!=controller.state)return structure;
        // A bounded scan may predate an explicit controller edit. Never migrate
        // stale legacy settings over the live tile; publish its latest data.
        if(((TileEntityVehicleController)raw).controlsInitialized())
            return VehicleSeatSettings.replaceAt(structure,controller.pos,raw.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin));
        VehicleStructure migrated=migrate(structure);if(migrated==structure)return structure;
        NBTTagCompound data=VehicleTileData.translated(migrated.controller().tileData(),origin);raw.readFromNBT(data);((TileEntityVehicleController)raw).resumeAfterLoad(world);raw.markDirty();
        world.notifyBlockUpdate(at,world.getBlockState(at),world.getBlockState(at),2);
        return migrated;
    }
}
