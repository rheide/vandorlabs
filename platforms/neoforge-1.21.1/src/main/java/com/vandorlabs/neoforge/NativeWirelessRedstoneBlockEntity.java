package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.neoforge.redstone.*;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Event-driven non-latch endpoint of the existing ranged numeric channel host. */
public final class NativeWirelessRedstoneBlockEntity extends BlockEntity implements NativeChannelMember {
    private ChannelList channels=ChannelList.EMPTY,transmission=ChannelList.EMPTY;
    private boolean analog=true,local,initialized;
    private int on=15,off,input,received,range,dirtyPorts;
    private CompoundTag retained=new CompoundTag();
    private RedstoneData.Source source=new RedstoneData.Source(0,false,false,0);
    private ControlMountSettings savedMount=ControlMountSettings.of(0,0,0);
    public NativeWirelessRedstoneBlockEntity(BlockPos pos,BlockState state){super(VandorLabsBootstrap.WIRELESS_ENTITY.get(),pos,state);range=transmitter()?16:-1;local=!transmitter();}
    public boolean transmitter(){return getBlockState().getBlock() instanceof NativeWirelessRedstoneBlock block&&block.transmitter();}
    public boolean analog(){return analog;}
    public int onLevel(){return on;}
    public int offLevel(){return off;}
    public Direction port(){return getBlockState().getValue(NativeWirelessRedstoneBlock.FACING);}
    public int getOutputLevel(){return transmitter()?0:WirelessRedstoneData.outputLevel(analog,received,on,off);}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public ChannelList transmittedChannels(){return transmission;}
    public ChannelList transmissionChannels(){return transmission;}
    @Override public int transmissionRange(){return range;}
    public boolean localRedstone(){return local;}
    @Override public boolean isChannelMemberActive(){return getBlockState().getBlock() instanceof NativeWirelessRedstoneBlock&&NativeChannelMember.super.isChannelMemberActive();}
    @Override public int localSignalLevel(int channel){return transmitter()?WirelessRedstoneData.sourceLevel(analog,input):0;}
    private ServerLevel server(){if(!(level instanceof ServerLevel server))return null;if(!server.getServer().isSameThread())throw new IllegalStateException("Wireless endpoint changes require the owning server thread");return NativeRedstoneChannels.open(server)?server:null;}
    @Override public void setChannelLevel(int value){if(level instanceof ServerLevel&&server()==null)return;int before=getOutputLevel();received=WirelessRedstoneData.level(value);if(before!=getOutputLevel())changed(true);}
    private void changed(boolean output){setChanged();var server=server();if(server==null)return;server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);if(output)notifyPhysical();}
    private void markPort(Direction port){dirtyPorts|=1<<port.get3DDataValue();}
    private void notifyPhysical(){markPort(port());markPort(port().getOpposite());if(NativeSignalUpdateBatch.isActive())NativeSignalUpdateBatch.afterSignals(this,this::emitPhysical);else NativeSignalUpdateBatch.apply(()->NativeSignalUpdateBatch.afterSignals(this,this::emitPhysical));}
    private void emitPhysical(){var server=server();if(server==null||!isChannelMemberActive())return;int ports=dirtyPorts;dirtyPorts=0;notifyAround(server,worldPosition,getBlockState().getBlock());for(Direction side:Direction.values())if((ports&(1<<side.get3DDataValue()))!=0)notifyAround(server,worldPosition.relative(side),getBlockState().getBlock());}
    static void notifyAround(ServerLevel world,BlockPos center,net.minecraft.world.level.block.Block block){if(!world.hasChunkAt(center))return;for(Direction side:Direction.values()){var next=center.relative(side);if(world.hasChunkAt(next))world.neighborChanged(next,block,center);}}
    public void sampleInput(){var server=server();if(server==null||!transmitter()||!isChannelMemberActive())return;NativeRedstoneChannels.batch(server,()->{if(sample(server)){NativeRedstoneChannels.outputChanged(this);changed(true);}});}
    private boolean sample(ServerLevel server){
        Direction side=port();BlockPos source=worldPosition.relative(side);int value=0;
        if(server.hasChunkAt(source)){var state=server.getBlockState(source);if(state.shouldCheckWeakPower(server,source,side)){for(Direction direction:Direction.values()){var neighbor=source.relative(direction);if(server.hasChunkAt(neighbor))value=Math.max(value,server.getBlockState(neighbor).getDirectSignal(server,neighbor,direction));}}else value=state.getSignal(server,source,side);}
        value=WirelessRedstoneData.level(value);if(value==input)return false;input=value;return true;
    }
    public boolean configure(ChannelList receive,boolean nextAnalog,int nextOn,int nextOff,Direction facing){
        if(receive==null||nextOn<0||nextOn>15||nextOff<0||nextOff>15||facing==null)return false;var server=server();if(server==null||!isChannelMemberActive())return false;
        NativeRedstoneChannels.batch(server,()->{NativeRedstoneChannels.unregister(this);Direction previous=port();markPort(previous);markPort(previous.getOpposite());channels=transmitter()?transmission:receive;analog=nextAnalog;on=nextOn;off=nextOff;server.setBlock(worldPosition,getBlockState().setValue(NativeWirelessRedstoneBlock.FACING,facing),2);if(transmitter())sample(server);NativeRedstoneChannels.channelsChanged(this);changed(true);});return true;
    }
    public boolean configureTransmission(ChannelList next,int radius,boolean nativeOutput){
        if(!transmitter()||!TransmissionSettings.validConfiguration(next,radius))return false;var server=server();if(server==null||!isChannelMemberActive())return false;
        if(next.equals(transmission)&&next.equals(channels)&&radius==range&&nativeOutput==local)return true;
        NativeRedstoneChannels.batch(server,()->{NativeRedstoneChannels.unregister(this);channels=transmission=next;range=radius;local=nativeOutput;var forge=retained.getCompound("ForgeData");var all=forge.getCompound(TransmissionSettings.SETTINGS);var setting=new CompoundTag();setting.putIntArray("Channels",next.toArray());setting.putInt("Range",radius);setting.putBoolean("Local",nativeOutput);all.put("main",setting);forge.put(TransmissionSettings.SETTINGS,all);retained.put("ForgeData",forge);NativeRedstoneChannels.channelsChanged(this);changed(true);});return true;
    }
    @Override public void beforeChannelRegistration(){var server=server();if(server==null||!isChannelMemberActive())return;if(transmitter())sample(server);initialized=true;changed(true);}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)NativeRedstoneChannels.queueRegistration(this);}
    @Override public void setRemoved(){NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    private static ChannelList readChannels(CompoundTag tag,String key,ChannelList fallback){try{return tag.contains(key,Tag.TAG_INT_ARRAY)?ChannelList.of(tag.getIntArray(key)):fallback;}catch(IllegalArgumentException malformed){return fallback;}}
    public CompoundTag configuration(){var tag=new CompoundTag();tag.putInt("Channel",channels.first());tag.putIntArray("RedstoneChannels",channels.toArray());ControlMountSettings.write(new ModernHousingTags(tag),savedMount.height,savedMount.tilt,savedMount.direction);tag.putBoolean(WirelessRedstoneData.ANALOG,analog);tag.putInt(WirelessRedstoneData.ON,on);tag.putInt(WirelessRedstoneData.OFF,off);return tag;}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);retained=tag.copy();for(String key:List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retained.remove(key);var data=new ModernHousingTags(tag);source=RedstoneData.Source.read(data);initialized=source.initialized;savedMount=ControlMountSettings.read(data,savedMount.height,savedMount.tilt,savedMount.direction);channels=readChannels(tag,"RedstoneChannels",ChannelList.of(source.channel));
        analog=WirelessRedstoneData.savedAnalog(tag.contains(WirelessRedstoneData.ANALOG),tag.getBoolean(WirelessRedstoneData.ANALOG));on=WirelessRedstoneData.savedOn(tag.contains(WirelessRedstoneData.ON,Tag.TAG_INT),tag.getInt(WirelessRedstoneData.ON));off=WirelessRedstoneData.level(tag.getInt(WirelessRedstoneData.OFF));input=WirelessRedstoneData.level(tag.getInt(WirelessRedstoneData.INPUT));received=WirelessRedstoneData.level(tag.getInt(WirelessRedstoneData.RECEIVED));
        var settings=tag.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");transmission=TransmissionSettings.readChannels(settings.contains("Channels",Tag.TAG_INT_ARRAY),settings.getIntArray("Channels"),transmitter()?channels:ChannelList.EMPTY);range=TransmissionSettings.readRange(settings.contains("Range",Tag.TAG_INT),settings.getInt("Range"),transmitter());local=TransmissionSettings.readLocal(settings.contains("Local",Tag.TAG_BYTE),settings.getBoolean("Local"),!transmitter());
        if(level!=null&&!level.isClientSide)NativeRedstoneChannels.queueRegistration(this);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.merge(retained);var data=new ModernHousingTags(tag);new RedstoneData.Source(channels.first(),source.localOn,initialized,source.mountRotation).write(data);tag.putIntArray("RedstoneChannels",channels.toArray());ControlMountSettings.write(data,savedMount.height,savedMount.tilt,savedMount.direction);if(!tag.contains("LatchedChannels"))tag.putIntArray("LatchedChannels",new int[0]);if(!tag.contains("LatchedLevels"))tag.putIntArray("LatchedLevels",new int[0]);tag.putBoolean(WirelessRedstoneData.ANALOG,analog);tag.putInt(WirelessRedstoneData.ON,on);tag.putInt(WirelessRedstoneData.OFF,off);tag.putInt(WirelessRedstoneData.INPUT,input);tag.putInt(WirelessRedstoneData.RECEIVED,received);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveWithoutMetadata(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
