package com.vandorlabs.tiles;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Persistent vehicle controls. Pilot Seat's older codec remains the migration format. */
public class TileEntityVehicleController extends TileEntityPilotSeat {
    private boolean initialized;
    private VehicleScan migration;
    private long retry;
    public boolean controlsInitialized(){return initialized;}
    public void initializeControls(){initialized=true;markDirty();}
    @Override public void setTaxiMode(boolean value){initializeControls();super.setTaxiMode(value);}
    @Override public void setVehicleChannels(ChannelList value,World real){initializeControls();super.setVehicleChannels(value,real);}
    @Override public void setModeChannels(int mode,ChannelList value){initializeControls();super.setModeChannels(mode,value);}
    @Override public void configure(int mode,int unused,World real){initializeControls();super.configure(mode,0,real);}
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
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("VehicleControllerControls",initialized);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);initialized=tag.getBoolean("VehicleControllerControls");}
}
