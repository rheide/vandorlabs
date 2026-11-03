package com.vandorlabs.tiles;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Persistent vehicle controls. Pilot Seat's older codec remains the migration format. */
public class TileEntityVehicleController extends TileEntityPilotSeat {
    public String vehicleName="";
    public boolean security;
    public int card;
    public VehicleShape shape;
    public double vehicleWeight=-1;
    public int vehicleWeightVersion;
    public boolean vehicleWeightIncomplete;
    public boolean converting;
    public void setConverting(boolean value){if(converting==value)return;converting=value;publishDesign();}
    public void designSettings(String name,boolean enabled,int accepted){
        if(accepted<0||accepted>6)throw new IllegalArgumentException("Invalid Access Card");
        vehicleName=com.vandorlabs.programmer.ChipJournal.bounded(name,40);security=enabled;card=accepted;
        markDirty();publishDesign();
    }
    public void publishDesign(){if(world!=null&&(!world.isRemote||world instanceof VehicleWorld))world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    private boolean initialized;
    private VehicleScan migration;
    private long retry;
    public boolean controlsInitialized(){return initialized;}
    public void initializeControls(){initialized=true;markDirty();}
    @Override public void setTaxiMode(boolean value){initializeControls();super.setTaxiMode(value);}
    @Override public void setVehicleChannels(ChannelList value,World real){initializeControls();super.setVehicleChannels(value,real);}
    @Override public void setModeChannels(int mode,ChannelList value){initializeControls();super.setModeChannels(mode,value);}
    @Override public void configure(int mode,int height,World real){initializeControls();super.configure(mode,height,real);}
    @Override public void update(){
        if(world==null || world.isRemote)return;
        if(!initialized){
            if(world.getTotalWorldTime()<retry)return;
            if(migration==null)migration=new VehicleScan(world,pos);
            if(!migration.step(256))return;
            migration=null;retry=world.getTotalWorldTime()+200;
            if(!initialized)return;
        }
        super.update();
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);if(!vehicleName.isEmpty())tag.setString("VehicleName",vehicleName);if(security||card!=0){tag.setBoolean("VehicleSecurity",security);tag.setInteger("VehicleCard",card);}if(shape!=null)shape.write(tag);if(Double.isFinite(vehicleWeight)&&vehicleWeight>=0){tag.setDouble("VehicleWeight",vehicleWeight);if(vehicleWeightVersion>0)tag.setInteger("VehicleWeightVersion",vehicleWeightVersion);if(vehicleWeightIncomplete)tag.setBoolean("VehicleWeightIncomplete",true);}tag.setBoolean("VehicleControllerControls",initialized);return tag;}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound tag=super.getUpdateTag();tag.setBoolean("VehicleConverting",converting);return tag;}
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket(){return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);initialized=tag.getBoolean("VehicleControllerControls");vehicleName=com.vandorlabs.programmer.ChipJournal.bounded(tag.getString("VehicleName"),40);security=tag.getBoolean("VehicleSecurity");card=Math.max(0,Math.min(6,tag.getInteger("VehicleCard")));shape=VehicleShape.read(tag);vehicleWeight=tag.hasKey("VehicleWeight",99)&&Double.isFinite(tag.getDouble("VehicleWeight"))&&tag.getDouble("VehicleWeight")>=0?tag.getDouble("VehicleWeight"):-1;vehicleWeightVersion=tag.getInteger("VehicleWeightVersion");vehicleWeightIncomplete=tag.getBoolean("VehicleWeightIncomplete");if(world!=null&&world.isRemote)converting=tag.getBoolean("VehicleConverting");}
}
