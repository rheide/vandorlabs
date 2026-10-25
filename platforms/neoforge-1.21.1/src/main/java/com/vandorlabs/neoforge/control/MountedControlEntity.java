package com.vandorlabs.neoforge.control;

import com.vandorlabs.neoforge.persistence.NbtPrimitiveData;
import com.vandorlabs.neoforge.redstone.ModernChannelMember;
import com.vandorlabs.neoforge.redstone.ModernRedstoneChannels;
import com.vandorlabs.persistence.ChannelMembershipData;
import com.vandorlabs.persistence.RedstoneData;
import com.vandorlabs.redstone.ChannelLatch;
import com.vandorlabs.redstone.ChannelList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Native lifecycle over the shared channel latch bank and stable legacy source codec. */
public final class MountedControlEntity extends BlockEntity implements ModernChannelMember,ChannelLatch {
    private ChannelList channels=ChannelList.EMPTY,latched=ChannelList.EMPTY;
    private final Map<Integer,Integer> levels=new HashMap<>();
    private boolean localOn,initialized;
    private int mountRotation,baseHeight,baseTilt,tiltDirection;
    public MountedControlEntity(BlockPos pos,BlockState state){super(ControlRegistration.ENTITY.get(),pos,state);}
    public boolean momentary(){return ((MountedControlBlock)getBlockState().getBlock()).momentary;}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public boolean isChannelLatch(){return !momentary();}
    @Override public int latchedLevel(int channel){return levels.getOrDefault(channel,latched.contains(channel)||!initialized&&getBlockState().getValue(MountedControlBlock.ON)&&channels.contains(channel)?15:0);}
    @Override public int localSignalLevel(int channel){return isChannelLatch()?latchedLevel(channel):(initialized?localOn:getBlockState().getValue(MountedControlBlock.ON))?15:0;}
    public boolean localOn(){return localOn;}
    public int outputLevel(){if(!localOn)return 0;int output=15;for(int i=0;i<channels.size();i++)output=Math.min(output,latchedLevel(channels.get(i)));return output;}
    public int mountRotation(){return mountRotation;}public int baseHeight(){return baseHeight;}public int baseTilt(){return baseTilt;}public int tiltDirection(){return tiltDirection;}
    @Override public net.neoforged.neoforge.client.model.data.ModelData getModelData(){return net.neoforged.neoforge.client.model.data.ModelData.builder().with(ControlGeometry.MOUNT,ControlGeometry.mountCode(this)).build();}
    @Override public void setChannelLevel(int level){}
    @Override public void applyLinkedLevels(Map<Integer,Integer> value){
        if(level==null||level.isClientSide||!isChannelLatch())return;
        boolean changed=!levels.equals(value);levels.clear();levels.putAll(value);
        int[] active=value.entrySet().stream().filter(entry->entry.getValue()>0).mapToInt(Map.Entry::getKey).toArray();
        ChannelList next=ChannelList.of(active).intersect(channels);boolean on=!channels.isEmpty()&&next.containsAll(channels);
        changed|=!latched.equals(next)||localOn!=on||!initialized;latched=next;localOn=on;initialized=true;if(changed)changed();
    }
    public void setLocalOn(boolean on){
        if(localOn==on&&initialized&&(on||latched.isEmpty()))return;
        levels.clear();localOn=on;latched=on?channels:ChannelList.EMPTY;initialized=true;setChanged();
        if(isChannelLatch()&&channels.first()>0)ModernRedstoneChannels.latchLevelChanged(this,on?15:0);else ModernRedstoneChannels.inputChanged(this);
        changed();
    }
    public boolean configure(ChannelList next,int height,int tilt,int direction){
        if(next==null||!com.vandorlabs.render.ControlMount.valid(height,tilt,direction))return false;
        if(!channels.equals(next)){latched=localOn?next:latched.intersect(next);channels=next;ModernRedstoneChannels.channelsChanged(this);}
        baseHeight=height;baseTilt=tilt;tiltDirection=direction;changed();return true;
    }
    public CompoundTag configuration(){var tag=new CompoundTag();tag.putIntArray("RedstoneChannels",channels.toArray());tag.putInt("RedstoneChannel",channels.first());tag.putInt("Channel",channels.first());writeMount(tag);return tag;}
    public boolean applyConfiguration(CompoundTag tag){
        ChannelList next;try{next=ChannelMembershipData.read(tag.contains("RedstoneChannels",11)?tag.getIntArray("RedstoneChannels"):null,ChannelList.of(Math.max(0,tag.getInt("Channel"))));}catch(IllegalArgumentException invalid){return false;}
        int tilt=com.vandorlabs.render.ControlMount.migratedTilt(tag.getInt("BaseTilt"),tag.contains("ControlMountVersion"));
        if(!com.vandorlabs.render.ControlMount.valid(tag.getInt("BaseHeight"),tilt,tag.getInt("TiltDirection")))return configure(next,baseHeight,baseTilt,tiltDirection);
        return configure(next,tag.getInt("BaseHeight"),tilt,tag.getInt("TiltDirection"));
    }
    private void writeMount(CompoundTag tag){tag.putInt("ControlMountVersion",2);tag.putInt("BaseHeight",baseHeight);tag.putInt("BaseTilt",baseTilt);tag.putInt("TiltDirection",tiltDirection);}
    public void placedRotation(int rotation){mountRotation=Math.floorMod(rotation,4);changed();}
    private void changed(){
        setChanged();requestModelDataUpdate();if(level==null||level.isClientSide)return;
        ModernRedstoneChannels.afterSignals(this,()->{
            BlockState state=getBlockState(),next=state.setValue(MountedControlBlock.ON,localOn).setValue(MountedControlBlock.ROTATION,state.getValue(MountedControlBlock.FACING).getAxis().isVertical()?mountRotation:0);
            if(next!=state)level.setBlock(worldPosition,next,3);level.sendBlockUpdated(worldPosition,next,next,3);
            for(Direction side:Direction.values()){
                BlockPos neighbor=worldPosition.relative(side);if(!level.hasChunkAt(neighbor))continue;
                level.neighborChanged(neighbor,next.getBlock(),worldPosition);
                if(level.getBlockState(neighbor).isRedstoneConductor(level,neighbor))for(Direction supportSide:Direction.values()){
                    BlockPos target=neighbor.relative(supportSide);if(level.hasChunkAt(target))level.neighborChanged(target,next.getBlock(),neighbor);
                }
            }
        });
    }
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)ModernRedstoneChannels.queueRegistration(this);}
    @Override public void channelLoaded(){if(!initialized){localOn=getBlockState().getValue(MountedControlBlock.ON);latched=localOn?channels:ChannelList.EMPTY;initialized=true;setChanged();}ModernRedstoneChannels.channelsChanged(this);if(momentary()&&localOn)level.scheduleTick(worldPosition,getBlockState().getBlock(),20);changed();}
    @Override public void setRemoved(){ModernRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){ModernRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);writeMount(tag);new RedstoneData.Source(channels.first(),localOn,initialized,mountRotation).write(new NbtPrimitiveData(tag));tag.putIntArray("RedstoneChannels",channels.toArray());tag.putIntArray("LatchedChannels",latched.toArray());int[] saved=new int[channels.size()];for(int i=0;i<saved.length;i++)saved[i]=latchedLevel(channels.get(i));tag.putIntArray("LatchedLevels",saved);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);var source=RedstoneData.Source.read(new NbtPrimitiveData(tag));channels=ChannelMembershipData.read(tag.contains("RedstoneChannels",11)?tag.getIntArray("RedstoneChannels"):null,ChannelList.of(source.channel));localOn=source.localOn;initialized=source.initialized;mountRotation=source.mountRotation;
        latched=ChannelMembershipData.read(tag.contains("LatchedChannels",11)?tag.getIntArray("LatchedChannels"):null,localOn?channels:ChannelList.EMPTY).intersect(channels);levels.clear();int[] saved=tag.getIntArray("LatchedLevels");for(int i=0;i<Math.min(saved.length,channels.size());i++)levels.put(channels.get(i),Math.max(0,Math.min(15,saved[i])));
        int h=tag.getInt("BaseHeight"),t=com.vandorlabs.render.ControlMount.migratedTilt(tag.getInt("BaseTilt"),tag.contains("ControlMountVersion")),d=tag.getInt("TiltDirection");if(com.vandorlabs.render.ControlMount.valid(h,t,d)){baseHeight=h;baseTilt=t;tiltDirection=d;}
        requestModelDataUpdate();
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveWithoutMetadata(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
