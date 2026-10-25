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
    private boolean inverted,received,requested;
    private java.util.UUID operator;
    private String couplingStatus="Ready";
    public java.util.UUID operator(){return operator;}
    public void authorize(net.minecraft.entity.player.EntityPlayer actor){net.minecraft.world.World parent=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).parent:world;if(actor!=null&&!actor.isDead&&!actor.isSpectator()&&actor.world==parent&&!parent.isRemote){operator=actor.getUniqueID();sync();}}
    public String status(){return couplingStatus;}
    public boolean requested(){return requested;}
    public void status(String value){if(!java.util.Objects.equals(couplingStatus,value)){couplingStatus=value;sync();}}
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
        boolean changed=requested!=(powered!=inverted);requested=powered!=inverted;
        if(changed&&carried)com.vandorlabs.vehicle.VehicleCouplings.wake(((com.vandorlabs.vehicle.VehicleWorld)world).craft);
        if(carried&&com.vandorlabs.vehicle.VehicleCouplings.hasPair(((com.vandorlabs.vehicle.VehicleWorld)world).craft,pos)){
            // Re-latching two heads already owned by this entity is immediate.
            // Releasing them waits for the complete detached section's parking preflight.
            if(requested&&!state.getValue(BlockCoupler.LOCKED))((com.vandorlabs.vehicle.VehicleWorld)world).craft.signals.couplerState(this,true);
            if(changed)sync();return;
        }
        if(state.getBlock() instanceof BlockCoupler&&state.getValue(BlockCoupler.LOCKED)!=(powered!=inverted)){
            if(carried){com.vandorlabs.vehicle.EntityGroundVehicle craft=((com.vandorlabs.vehicle.VehicleWorld)world).craft;if(craft!=null)craft.signals.couplerState(this,powered!=inverted);}
            else world.setBlockState(pos,state.withProperty(BlockCoupler.LOCKED,powered!=inverted),2);
            sync();
        }
        else if(changed)sync();
    }
    private void sync(){markDirty();if(world!=null&&(!world.isRemote||world instanceof com.vandorlabs.vehicle.VehicleWorld))world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    @Override public void onLoad(){super.onLoad();DeferredTileLoad.schedule(this,()->{if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld))RedstoneChannels.register(this);sample();});}
    @Override public void invalidate(){RedstoneChannels.unregister(this);super.invalidate();}
    @Override public void onChunkUnload(){RedstoneChannels.unregister(this);super.onChunkUnload();}
    @Override public boolean shouldRefresh(net.minecraft.world.World w,BlockPos p,net.minecraft.block.state.IBlockState old,net.minecraft.block.state.IBlockState next){return old.getBlock()!=next.getBlock();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);ChannelData.write(tag,channels);tag.setBoolean("CouplerInverted",inverted);tag.setBoolean("CouplerReceived",received);tag.setBoolean("CouplerRequested",requested);if(operator!=null)tag.setUniqueId("CouplerActor",operator);tag.setString("CouplerStatus",couplingStatus);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);channels=ChannelData.read(tag,0);inverted=tag.getBoolean("CouplerInverted");received=tag.getBoolean("CouplerReceived");requested=tag.hasKey("CouplerRequested",1)?tag.getBoolean("CouplerRequested"):received!=inverted;operator=tag.hasUniqueId("CouplerActor")?tag.getUniqueId("CouplerActor"):null;couplingStatus=tag.hasKey("CouplerStatus",8)?tag.getString("CouplerStatus"):"Ready";}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket(){return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(net.minecraft.network.NetworkManager manager,net.minecraft.network.play.server.SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());world.markBlockRangeForRenderUpdate(pos,pos);}
}
