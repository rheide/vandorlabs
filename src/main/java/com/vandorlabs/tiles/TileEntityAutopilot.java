package com.vandorlabs.tiles;

import com.vandorlabs.redstone.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;

/** Receiver with a pulse latch. Falling power does not cancel a journey. */
public class TileEntityAutopilot extends TileEntity implements RedstoneChannelMember {
    private ChannelList channels=ChannelList.EMPTY;
    public int trigger=1;
    public String marker="minecraft:concrete";
    public int markerMeta;
    private boolean high,enabled;
    public String status="Off";
    public long departureAt;
    public net.minecraft.util.math.BlockPos departureSeat;
    public java.util.UUID departureOwner;
    public int departureMode=1,departureHeight;
    public boolean departureDirect=true;
    public net.minecraft.util.math.BlockPos departureWaypoint;
    public boolean enabled(){return enabled;}
    public void pulseDeparture(){setEnabled(true);scheduleStart();}
    private void scheduleStart(){
        if(world==null||world.isRemote||world instanceof com.vandorlabs.vehicle.VehicleWorld||this instanceof TileEntityWaypoint)return;
        if(departureSeat!=null)departureDirect=true;
        departureAt=world.getTotalWorldTime()+1;world.scheduleUpdate(pos,getBlockType(),1);changed();
    }
    public boolean powered(){return trigger==2?!high:trigger==1&&high;}
    public void setEnabled(boolean value){if(enabled==value)return;enabled=value;status=value?"Following route":"Off";if(value)scheduleStart();changed();}
    public void setStatus(String value){if(!status.equals(value)){status=value;changed();}}
    public TileEntity channelTile(){return this;}
    public int getRedstoneChannel(){return channels.first();}
    public ChannelList getRedstoneChannels(){return channels;}
    public void setRedstoneChannel(int value){setRedstoneChannels(ChannelList.of(value));}
    public void setRedstoneChannels(ChannelList value){ChannelList old=channels;channels=value;if(world!=null && !(world instanceof com.vandorlabs.vehicle.VehicleWorld))RedstoneChannels.channelChanged(this,old);changed();}
    public boolean hasLocalRedstoneSignal(){return world!=null && !(world instanceof com.vandorlabs.vehicle.VehicleWorld) && LoadedRedstonePower.isPowered(world,pos);}
    public void setChannelSignal(boolean value){boolean before=powered();if(high!=value){high=value;changed();}if(!before&&powered())pulseDeparture();}
    public void refreshPower(){if(world!=null&&!world.isRemote){boolean power=hasLocalRedstoneSignal();for(int i=0;i<channels.size();i++)power|=RedstoneChannels.level(world,channels.get(i))>0;setChannelSignal(power);}}
    public void changed(){markDirty();if(world!=null&&(!world.isRemote||world instanceof com.vandorlabs.vehicle.VehicleWorld))world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote&&!(world instanceof com.vandorlabs.vehicle.VehicleWorld)){RedstoneChannels.register(this);if(enabled){if(departureAt<=0)departureAt=world.getTotalWorldTime()+1;world.scheduleUpdate(pos,getBlockType(),1);}}}
    @Override public void invalidate(){RedstoneChannels.unregister(this);super.invalidate();}
    @Override public void onChunkUnload(){RedstoneChannels.unregister(this);super.onChunkUnload();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);ChannelData.write(n,channels);n.setInteger("AutopilotTrigger",trigger);n.setString("RouteMarker",marker);n.setInteger("RouteMarkerMeta",markerMeta);n.setBoolean("AutopilotHigh",high);n.setBoolean("AutopilotEnabled",enabled);n.setString("AutopilotStatus",status);n.setLong("AutopilotDepartureAt",departureAt);if(departureSeat!=null)n.setLong("AutopilotDepartureSeat",departureSeat.toLong());if(departureOwner!=null)n.setUniqueId("AutopilotDepartureOwner",departureOwner);n.setInteger("AutopilotDepartureMode",departureMode);n.setInteger("AutopilotDepartureHeight",departureHeight);n.setBoolean("AutopilotDepartureDirect",departureDirect);if(departureWaypoint!=null)n.setLong("AutopilotDepartureWaypoint",departureWaypoint.toLong());return n;}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);channels=ChannelData.read(n,0);trigger=n.hasKey("AutopilotTrigger")?Math.max(0,Math.min(2,n.getInteger("AutopilotTrigger"))):1;marker=n.hasKey("RouteMarker")?n.getString("RouteMarker"):"minecraft:concrete";markerMeta=Math.max(0,Math.min(15,n.getInteger("RouteMarkerMeta")));high=n.getBoolean("AutopilotHigh");enabled=n.getBoolean("AutopilotEnabled");status=n.hasKey("AutopilotStatus")?n.getString("AutopilotStatus"):"Off";departureAt=n.getLong("AutopilotDepartureAt");departureSeat=n.hasKey("AutopilotDepartureSeat")?net.minecraft.util.math.BlockPos.fromLong(n.getLong("AutopilotDepartureSeat")):null;departureOwner=n.hasUniqueId("AutopilotDepartureOwner")?n.getUniqueId("AutopilotDepartureOwner"):null;departureMode=n.getInteger("AutopilotDepartureMode");departureHeight=n.getInteger("AutopilotDepartureHeight");departureDirect=!n.hasKey("AutopilotDepartureDirect")||n.getBoolean("AutopilotDepartureDirect");departureWaypoint=n.hasKey("AutopilotDepartureWaypoint")?net.minecraft.util.math.BlockPos.fromLong(n.getLong("AutopilotDepartureWaypoint")):null;}
    public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
}
