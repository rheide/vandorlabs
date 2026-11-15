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
    private com.vandorlabs.rack.LegacyAutopilotData legacyData;
    public String status="Off";
    public long departureAt;
    public net.minecraft.util.math.BlockPos departureSeat;
    public java.util.UUID departureOwner;
    public int departureMode=1,departureHeight;
    public boolean departureDirect=true;
    public net.minecraft.util.math.BlockPos departureWaypoint;
    public boolean enabled(){return enabled;}
    public void pulseDeparture(){setEnabled(true);scheduleStart();}
    protected boolean hasNavigationReceiver(){return true;}
    protected boolean canDepart(){return true;}
    private void scheduleStart(){
        if(!canDepart()||world==null||world.isRemote||world instanceof com.vandorlabs.vehicle.VehicleWorld||this instanceof TileEntityWaypoint)return;
        if(departureSeat!=null)departureDirect=true;
        departureAt=world.getTotalWorldTime()+1;world.scheduleUpdate(pos,getBlockType(),1);changed();
    }
    public boolean powered(){return com.vandorlabs.rack.AutopilotModuleSettings.powered(trigger,high);}
    public void setEnabled(boolean value){if(enabled==value)return;enabled=value;status=value?"Following route":"Off";if(value)scheduleStart();changed();}
    public void setStatus(String value){if(!status.equals(value)){status=value;changed();}}
    public TileEntity channelTile(){return this;}
    public int getRedstoneChannel(){return channels.first();}
    public ChannelList getRedstoneChannels(){return channels;}
    public void setRedstoneChannel(int value){setRedstoneChannels(ChannelList.of(value));}
    public void setRedstoneChannels(ChannelList value){ChannelList old=channels;channels=value;if(world!=null && !(world instanceof com.vandorlabs.vehicle.VehicleWorld))RedstoneChannels.channelChanged(this,old);changed();}
    public boolean hasLocalRedstoneSignal(){return world!=null && !(world instanceof com.vandorlabs.vehicle.VehicleWorld) && LoadedRedstonePower.isPowered(world,pos);}
    public void setChannelSignal(boolean value){boolean before=powered();if(high!=value){high=value;changed();}if(!before&&powered())pulseDeparture();}
    public void refreshPower(){if(world!=null&&!world.isRemote){RedstoneChannels.inputChanged(this);boolean power=hasLocalRedstoneSignal();for(int i=0;i<channels.size();i++)power|=RedstoneChannels.level(world,pos,channels.get(i))>0;setChannelSignal(power);}}
    public void changed(){markDirty();if(world!=null&&(!world.isRemote||world instanceof com.vandorlabs.vehicle.VehicleWorld))world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    /** Vanilla's default factory does not bind World before decoding external capabilities. */
    @Override protected void setWorldCreate(net.minecraft.world.World value){setWorld(value);}
    @Override public void onLoad(){WorldActionAuthorization.resumed(this);super.onLoad();if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld))DeferredTileLoad.schedule(this,this::loadedAfterWorld);}
    private void loadedAfterWorld(){
        if(getClass()==TileEntityAutopilot.class){
            // A parked legacy snapshot must finish its existing publication before compatibility conversion.
            if(com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)){DeferredTileLoad.schedule(this,this::loadedAfterWorld);return;}
            if(com.vandorlabs.rack.LegacyAutopilotMigration.convert(this)!=null)return;
        }
        if(hasNavigationReceiver())RedstoneChannels.register(this);
        if(enabled&&canDepart()){if(departureAt<=0)departureAt=world.getTotalWorldTime()+1;world.scheduleUpdate(pos,getBlockType(),1);}
    }
    @Override public void invalidate(){WorldActionAuthorization.revoke(this);RedstoneChannels.unregister(this);super.invalidate();}
    @Override public void onChunkUnload(){WorldActionAuthorization.unloaded(this);RedstoneChannels.unregister(this);super.onChunkUnload();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){n=writeAutopilotData(writeTileIdentity(n));return legacyData==null?n:legacyData.preserve(n);}
    /** Rack hosts save their physical identity separately from module-owned navigation state. */
    protected NBTTagCompound writeTileIdentity(NBTTagCompound n){return super.writeToNBT(n);}
    public NBTTagCompound writeAutopilotData(NBTTagCompound n){ChannelData.write(n,channels);n.setInteger("AutopilotTrigger",trigger);n.setString("RouteMarker",marker);n.setInteger("RouteMarkerMeta",markerMeta);n.setBoolean("AutopilotHigh",high);n.setBoolean("AutopilotEnabled",enabled);n.setString("AutopilotStatus",status);n.setLong("AutopilotDepartureAt",departureAt);if(departureSeat!=null)n.setLong("AutopilotDepartureSeat",departureSeat.toLong());if(departureOwner!=null)n.setUniqueId("AutopilotDepartureOwner",departureOwner);n.setInteger("AutopilotDepartureMode",departureMode);n.setInteger("AutopilotDepartureHeight",departureHeight);n.setBoolean("AutopilotDepartureDirect",departureDirect);if(departureWaypoint!=null)n.setLong("AutopilotDepartureWaypoint",departureWaypoint.toLong());return n;}
    @Override public void readFromNBT(NBTTagCompound n){NBTTagCompound original=n;n=com.vandorlabs.redstone.TransmitterSlots.normalize(n);WorldActionAuthorization.reading(this);super.readFromNBT(n);readAutopilotData(n);legacyData=getClass()==TileEntityAutopilot.class&&original.getString("id").equals("vandorlabs:autopilot")?new com.vandorlabs.rack.LegacyAutopilotData(original,writeTileIdentity(new NBTTagCompound())):null;}
    /** Read only the established receiver/navigation fields, without rebinding physical-world authority. */
    public void readAutopilotData(NBTTagCompound n){channels=ChannelData.read(n,0);trigger=n.hasKey("AutopilotTrigger")?Math.max(0,Math.min(2,n.getInteger("AutopilotTrigger"))):1;marker=n.hasKey("RouteMarker")?n.getString("RouteMarker"):"minecraft:concrete";markerMeta=Math.max(0,Math.min(15,n.getInteger("RouteMarkerMeta")));high=n.getBoolean("AutopilotHigh");enabled=n.getBoolean("AutopilotEnabled");status=n.hasKey("AutopilotStatus")?n.getString("AutopilotStatus"):"Off";departureAt=n.getLong("AutopilotDepartureAt");departureSeat=n.hasKey("AutopilotDepartureSeat")?net.minecraft.util.math.BlockPos.fromLong(n.getLong("AutopilotDepartureSeat")):null;departureOwner=n.hasUniqueId("AutopilotDepartureOwner")?n.getUniqueId("AutopilotDepartureOwner"):null;departureMode=n.getInteger("AutopilotDepartureMode");departureHeight=n.getInteger("AutopilotDepartureHeight");departureDirect=!n.hasKey("AutopilotDepartureDirect")||n.getBoolean("AutopilotDepartureDirect");departureWaypoint=n.hasKey("AutopilotDepartureWaypoint")?net.minecraft.util.math.BlockPos.fromLong(n.getLong("AutopilotDepartureWaypoint")):null;}
    public NBTTagCompound getUpdateTag(){if(legacyData==null)return writeToNBT(new NBTTagCompound());NBTTagCompound n=writeAutopilotData(writeTileIdentity(new NBTTagCompound()));n.removeTag("ForgeCaps");n.removeTag("ForgeData");return n;}
    public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
}
