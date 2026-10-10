package com.vandorlabs.tiles;

import com.vandorlabs.redstone.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.network.NetworkManager;
import java.util.UUID;

/** Receiver-only seat: channel edges survive conversion and do not feed the redstone bus. */
public final class TileEntityPilotSeat extends TileEntity implements RedstoneChannelMember,ITickable {
    private ChannelList channels=ChannelList.EMPTY;
    private boolean high,baselineAfterLoad;
    private long readyAt;
    public UUID owner;
    public TileEntity channelTile(){return this;}
    public int getRedstoneChannel(){return channels.first();}
    public ChannelList getRedstoneChannels(){return channels;}
    public void setRedstoneChannel(int channel){setRedstoneChannels(ChannelList.of(channel));}
    public void setRedstoneChannels(ChannelList next){channels=next;readyAt=0;baselineAfterLoad=false;high=powered(world);markDirty();sync();}
    public boolean hasLocalRedstoneSignal(){return false;}
    public void setChannelSignal(boolean signal){} // Polled against the real world, including in vehicle mode.
    private boolean powered(World real){if(real==null)return false;for(int n=0;n<channels.size();n++)if(RedstoneChannels.level(real,channels.get(n))>0)return true;return false;}
    /** -1 unchanged, 0 falling edge, 1 rising edge. */
    public void resumeAfterLoad(World real){readyAt=real.getTotalWorldTime()+20;baselineAfterLoad=true;}
    @Override public void onLoad(){if(world!=null && !world.isRemote)resumeAfterLoad(world);}
    public int poll(World real) {if(real.getTotalWorldTime()<readyAt)return -1;boolean next=powered(real);if(baselineAfterLoad){baselineAfterLoad=false;high=next;markDirty();return -1;}if(next==high)return -1;high=next;markDirty();return next?1:0;}
    public void update(){if(world!=null && !world.isRemote && poll(world)==1)VehicleService.signal(this,null);}
    private void sync(){if(world!=null && !world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);ChannelData.write(tag,channels);tag.setBoolean("VehicleSignalHigh",high);if(owner!=null)tag.setUniqueId("VehicleOwner",owner);return tag;}
    public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);channels=ChannelData.read(tag,0);high=tag.getBoolean("VehicleSignalHigh");owner=tag.hasUniqueId("VehicleOwner")?tag.getUniqueId("VehicleOwner"):null;}
    public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
}
