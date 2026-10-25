package com.vandorlabs.coupler;

import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.DeferredTileLoad;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Input-only coupling gate; each half must be enabled to join two structures. */
public final class TileCoupler extends TileEntity implements RedstoneChannelMember {
    private ChannelList channels=ChannelList.EMPTY;
    private boolean inverted,received;
    public boolean inverted(){return inverted;}
    public boolean connected(){return world!=null&&world.getBlockState(pos).getBlock() instanceof BlockCoupler&&world.getBlockState(pos).getValue(BlockCoupler.LOCKED);}
    @Override public TileEntity channelTile(){return this;}
    @Override public int getRedstoneChannel(){return channels.first();}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public void setRedstoneChannel(int channel){setRedstoneChannels(ChannelList.of(Math.max(0,channel)));}
    @Override public void setRedstoneChannels(ChannelList value){if(channels.equals(value))return;ChannelList old=channels;channels=value;RedstoneChannels.channelChanged(this,old);sample();}
    @Override public boolean hasLocalRedstoneSignal(){return false;}
    @Override public void setChannelSignal(boolean powered){received=powered;sample();}
    public void configure(ChannelList value,boolean invert){inverted=invert;setRedstoneChannels(value);sample();sync();}
    public void sample(){
        boolean carried=world instanceof com.vandorlabs.vehicle.VehicleWorld;
        net.minecraft.world.World parent=carried?((com.vandorlabs.vehicle.VehicleWorld)world).parent:world;
        if(parent==null||parent.isRemote||world.getTileEntity(pos)!=this)return;
        boolean powered=received;
        for(EnumFacing side:EnumFacing.values()){BlockPos p=pos.offset(side);if(world.isBlockLoaded(p))powered|=world.getRedstonePower(p,side)>0;}
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(state.getBlock() instanceof BlockCoupler&&state.getValue(BlockCoupler.LOCKED)!=(powered!=inverted)){
            if(carried){com.vandorlabs.vehicle.EntityGroundVehicle craft=((com.vandorlabs.vehicle.VehicleWorld)world).craft;if(craft!=null)craft.signals.couplerState(this,powered!=inverted);}
            else world.setBlockState(pos,state.withProperty(BlockCoupler.LOCKED,powered!=inverted),2);
            sync();
        }
    }
    private void sync(){markDirty();if(world!=null&&(!world.isRemote||world instanceof com.vandorlabs.vehicle.VehicleWorld))world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    @Override public void onLoad(){super.onLoad();DeferredTileLoad.schedule(this,()->{if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld))RedstoneChannels.register(this);sample();});}
    @Override public void invalidate(){RedstoneChannels.unregister(this);super.invalidate();}
    @Override public void onChunkUnload(){RedstoneChannels.unregister(this);super.onChunkUnload();}
    @Override public boolean shouldRefresh(net.minecraft.world.World w,BlockPos p,net.minecraft.block.state.IBlockState old,net.minecraft.block.state.IBlockState next){return old.getBlock()!=next.getBlock();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);ChannelData.write(tag,channels);tag.setBoolean("CouplerInverted",inverted);tag.setBoolean("CouplerReceived",received);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);channels=ChannelData.read(tag,0);inverted=tag.getBoolean("CouplerInverted");received=tag.getBoolean("CouplerReceived");}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket(){return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(net.minecraft.network.NetworkManager manager,net.minecraft.network.play.server.SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());world.markBlockRangeForRenderUpdate(pos,pos);}
}
