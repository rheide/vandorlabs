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
    private boolean taxiMode=true;
    private int redstoneMode=1,height;
    public int getRedstoneMode(){return redstoneMode;}
    public int getHeight(){return height;}
    public void setHeight(int value){
        if(value<0 || value>3)throw new IllegalArgumentException("Invalid Pilot Seat height");
        height=value;markDirty();sync();
        if(world!=null){world.markBlockRangeForRenderUpdate(pos,pos.up());
            for(com.vandorlabs.entity.EntityChairSeat mount:world.getEntitiesWithinAABB(com.vandorlabs.entity.EntityChairSeat.class,new net.minecraft.util.math.AxisAlignedBB(pos).grow(2)))
                if(pos.equals(mount.getChairPos()))mount.setSeatY(seatHeight());
        }
    }
    public void configure(int mode,int height,World real){
        if(mode<0 || mode>2 || height<0 || height>3)throw new IllegalArgumentException("Invalid Pilot Seat settings");
        this.redstoneMode=mode;readyAt=0;baselineAfterLoad=false;high=powered(real);setHeight(height);
        if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos.up());
    }
    public double seatHeight(){return com.vandorlabs.blocks.BlockPilotSeat.SEAT_HEIGHT+height/8D;}
    public boolean isTaxiMode(){return taxiMode;}
    public void setTaxiMode(boolean value){taxiMode=value;markDirty();sync();}
    public UUID owner;
    public TileEntity channelTile(){return this;}
    public int getRedstoneChannel(){return channels.first();}
    public ChannelList getRedstoneChannels(){return channels;}
    public void setRedstoneChannel(int channel){setRedstoneChannels(ChannelList.of(channel));}
    public void setRedstoneChannels(ChannelList next){setVehicleChannels(next,world);}
    public void setVehicleChannels(ChannelList next,World real){channels=next;readyAt=0;baselineAfterLoad=false;high=powered(real);markDirty();sync();}
    public boolean hasLocalRedstoneSignal(){return false;}
    public void setChannelSignal(boolean signal){} // Polled against the real world, including in vehicle mode.
    private boolean powered(World real){
        if(real==null || redstoneMode==0 || channels.size()==0 || channels.first()==0)return false;
        boolean signal=false;for(int n=0;n<channels.size();n++)if(RedstoneChannels.level(real,channels.get(n))>0){signal=true;break;}
        return redstoneMode==2?!signal:signal;
    }
    /** -1 unchanged, 0 falling edge, 1 rising edge. */
    public void resumeAfterLoad(World real){readyAt=real.getTotalWorldTime()+20;baselineAfterLoad=true;}
    @Override public void onLoad(){if(world!=null && !world.isRemote)resumeAfterLoad(world);}
    public int poll(World real) {if(real.getTotalWorldTime()<readyAt)return -1;boolean next=powered(real);if(baselineAfterLoad){baselineAfterLoad=false;high=next;markDirty();return -1;}if(next==high)return -1;high=next;markDirty();return next?1:0;}
    public void update(){if(world!=null && !world.isRemote && poll(world)==1)VehicleService.signal(this,null);}
    private void sync(){if(world!=null && !world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);ChannelData.write(tag,channels);tag.setBoolean("TaxiMode",taxiMode);tag.setInteger("PilotRedstoneMode",redstoneMode);tag.setInteger("PilotHeight",height);tag.setBoolean("VehicleSignalHigh",high);if(owner!=null)tag.setUniqueId("VehicleOwner",owner);return tag;}
    public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);channels=ChannelData.read(tag,0);redstoneMode=tag.hasKey("PilotRedstoneMode",3)?Math.max(0,Math.min(2,tag.getInteger("PilotRedstoneMode"))):1;height=Math.max(0,Math.min(3,tag.getInteger("PilotHeight")));taxiMode=!tag.hasKey("TaxiMode",1) || tag.getBoolean("TaxiMode");high=tag.getBoolean("VehicleSignalHigh");owner=tag.hasUniqueId("VehicleOwner")?tag.getUniqueId("VehicleOwner"):null;}
    public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());setHeight(height);}
}
