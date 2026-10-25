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
    private int low=5,high=15,selectedStep,powerSize=-1;
    public MountedControlEntity(BlockPos pos,BlockState state){super(ControlRegistration.ENTITY.get(),pos,state);}
    public MountedControlBlock block(){return (MountedControlBlock)getBlockState().getBlock();}
    public boolean powerLever(){return block() instanceof MountedPowerLeverBlock;}
    public boolean signalControl(){return block() instanceof MountedSignalControlBlock;}
    public com.vandorlabs.ui.ControlDialog.Profile profile(){return powerLever()?((MountedPowerLeverBlock)block()).twin?com.vandorlabs.ui.ControlDialog.Profile.TWIN_POWER:com.vandorlabs.ui.ControlDialog.Profile.INDUSTRIAL_POWER:signalControl()?((MountedSignalControlBlock)block()).selectableType()?com.vandorlabs.ui.ControlDialog.Profile.THROTTLE:com.vandorlabs.ui.ControlDialog.Profile.WALL_SLIDER:com.vandorlabs.ui.ControlDialog.Profile.BINARY;}
    public int lowLimit(){return low;}public int highLimit(){return high;}
    public int controlType(){return signalControl()?Math.max(0,((MountedSignalControlBlock)block()).controlType):0;}
    public int powerLeverSize(){return com.vandorlabs.persistence.ControlSettings.size(powerSize,powerLever()?((MountedPowerLeverBlock)block()).defaultSize:0);}
    public int step(){return signalControl()&&!channels.isEmpty()?com.vandorlabs.redstone.RedstoneScreenPolicy.stepForLevel(outputLevel(),low,high):selectedStep;}
    public int levelForStep(int step){return com.vandorlabs.redstone.RedstoneScreenPolicy.levelForStep(step,low,high);}
    public boolean momentary(){return ((MountedControlBlock)getBlockState().getBlock()).momentary;}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public boolean isChannelLatch(){return !momentary();}
    @Override public int latchedLevel(int channel){return levels.getOrDefault(channel,latched.contains(channel)||!initialized&&block().active(getBlockState())&&channels.contains(channel)?15:0);}
    @Override public int localSignalLevel(int channel){return isChannelLatch()?latchedLevel(channel):(initialized?localOn:block().active(getBlockState()))?15:0;}
    public boolean localOn(){return localOn;}
    public int outputLevel(){if(signalControl()&&channels.isEmpty())return levelForStep(selectedStep);if(!localOn)return 0;int output=15;for(int i=0;i<channels.size();i++)output=Math.min(output,latchedLevel(channels.get(i)));return output;}
    public int mountRotation(){return mountRotation;}public int baseHeight(){return baseHeight;}public int baseTilt(){return baseTilt;}public int tiltDirection(){return tiltDirection;}
    @Override public net.neoforged.neoforge.client.model.data.ModelData getModelData(){return net.neoforged.neoforge.client.model.data.ModelData.builder().with(ControlGeometry.MOUNT,ControlGeometry.mountCode(this)).with(ControlGeometry.SIZE,powerLeverSize()).build();}
    @Override public void setChannelLevel(int level){}
    @Override public void applyLinkedLevels(Map<Integer,Integer> value){
        if(level==null||level.isClientSide||!isChannelLatch())return;
        boolean changed=!levels.equals(value);levels.clear();levels.putAll(value);
        int[] active=value.entrySet().stream().filter(entry->entry.getValue()>0).mapToInt(Map.Entry::getKey).toArray();
        ChannelList next=ChannelList.of(active).intersect(channels);boolean on=!channels.isEmpty()&&next.containsAll(channels);
        changed|=!latched.equals(next)||localOn!=on||!initialized;latched=next;localOn=on;initialized=true;if(changed)changed();
    }
    public void setStep(int value){
        if(!signalControl()||value<0||value>3)return;
        selectedStep=value;
        if(channels.isEmpty()){localOn=value>0;initialized=true;latched=ChannelList.EMPTY;levels.clear();ModernRedstoneChannels.inputChanged(this);}
        else ModernRedstoneChannels.latchLevelChanged(this,levelForStep(value));
        changed();
    }
    public void setLocalOn(boolean on){
        if(signalControl()){setStep(on?3:0);return;}
        if(localOn==on&&initialized&&(on||latched.isEmpty()))return;
        levels.clear();localOn=on;latched=on?channels:ChannelList.EMPTY;initialized=true;setChanged();
        if(isChannelLatch()&&channels.first()>0)ModernRedstoneChannels.latchLevelChanged(this,on?15:0);else ModernRedstoneChannels.inputChanged(this);
        changed();
    }
    public boolean configure(ChannelList next,int height,int tilt,int direction){
        if(next==null||!com.vandorlabs.render.ControlMount.valid(height,tilt,direction))return false;
        if(!channels.equals(next)){int previous=outputLevel(),selection=step();latched=localOn?next:latched.intersect(next);channels=next;ModernRedstoneChannels.channelsChanged(this);if(signalControl()){if(next.isEmpty())setStep(selection);else if(previous>0)ModernRedstoneChannels.latchLevelChanged(this,previous);}}
        baseHeight=height;baseTilt=tilt;tiltDirection=direction;changed();return true;
    }
    public boolean configureOptions(ChannelList next,int height,int tilt,int direction,int low,int high,int type,int size){
        if(next==null||!com.vandorlabs.render.ControlMount.valid(height,tilt,direction))return false;
        if(signalControl()&&(!com.vandorlabs.redstone.RedstoneScreenPolicy.validLimits(low,high)||!com.vandorlabs.persistence.ControlSettings.validType(type)))return false;
        if(powerLever()&&!com.vandorlabs.persistence.ControlSettings.validSize(size))return false;
        int selected=step();
        if(signalControl()){this.low=low;this.high=high;setStep(selected);}
        if(powerLever())powerSize=size;
        if(!configure(next,height,tilt,direction))return false;
        if(signalControl()&&((MountedSignalControlBlock)block()).selectableType())configureType(type);
        return true;
    }
    private void configureType(int type){
        if(level==null||level.isClientSide||!signalControl()||!((MountedSignalControlBlock)block()).selectableType()||controlType()==type)return;
        BlockState before=getBlockState(),next=ControlRegistration.signal(type).defaultBlockState().setValue(MountedControlBlock.FACING,before.getValue(MountedControlBlock.FACING)).setValue(MountedControlBlock.ON,before.getValue(MountedControlBlock.ON)).setValue(MountedControlBlock.ROTATION,before.getValue(MountedControlBlock.ROTATION)).setValue(MountedSignalControlBlock.LEVEL,step());
        level.setBlock(worldPosition,next,3);setBlockState(next);clearRemoved();level.setBlockEntity(this);changed();
    }
    public CompoundTag configuration(){var tag=new CompoundTag();tag.putIntArray("RedstoneChannels",channels.toArray());tag.putInt("RedstoneChannel",channels.first());tag.putInt("Channel",channels.first());writeMount(tag);if(powerLever())tag.putInt("PowerLeverSize",powerLeverSize());if(signalControl()){tag.putInt("LowLimit",low);tag.putInt("HighLimit",high);if(((MountedSignalControlBlock)block()).selectableType())tag.putInt("ControlType",controlType());}return tag;}
    public boolean applyConfiguration(CompoundTag tag){
        ChannelList next;try{next=ChannelMembershipData.read(tag.contains("RedstoneChannels",11)?tag.getIntArray("RedstoneChannels"):null,ChannelList.of(Math.max(0,tag.getInt("Channel"))));}catch(IllegalArgumentException invalid){return false;}
        if(signalControl()){int l=tag.getInt("LowLimit"),h=tag.getInt("HighLimit");if(com.vandorlabs.redstone.RedstoneScreenPolicy.validLimits(l,h)){low=l;high=h;}}
        if(powerLever()&&tag.contains("PowerLeverSize",3)&&com.vandorlabs.persistence.ControlSettings.validSize(tag.getInt("PowerLeverSize")))powerSize=tag.getInt("PowerLeverSize");
        if(signalControl()&&tag.contains("ControlType",3)&&com.vandorlabs.persistence.ControlSettings.validType(tag.getInt("ControlType")))configureType(tag.getInt("ControlType"));
        int tilt=com.vandorlabs.render.ControlMount.migratedTilt(tag.getInt("BaseTilt"),tag.contains("ControlMountVersion"));
        if(!com.vandorlabs.render.ControlMount.valid(tag.getInt("BaseHeight"),tilt,tag.getInt("TiltDirection")))return configure(next,baseHeight,baseTilt,tiltDirection);
        return configure(next,tag.getInt("BaseHeight"),tilt,tag.getInt("TiltDirection"));
    }
    private void writeMount(CompoundTag tag){tag.putInt("ControlMountVersion",2);tag.putInt("BaseHeight",baseHeight);tag.putInt("BaseTilt",baseTilt);tag.putInt("TiltDirection",tiltDirection);}
    public void placedRotation(int rotation){mountRotation=Math.floorMod(rotation,4);changed();}
    private void changed(){
        setChanged();requestModelDataUpdate();if(level==null||level.isClientSide)return;
        ModernRedstoneChannels.afterSignals(this,()->{
            BlockState state=getBlockState(),next=block().withMountRotation(block().withActive(state,localOn),mountRotation);if(signalControl())next=next.setValue(MountedSignalControlBlock.LEVEL,step());
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
    @Override public void channelLoaded(){if(!initialized){localOn=block().active(getBlockState());latched=localOn?channels:ChannelList.EMPTY;initialized=true;setChanged();}ModernRedstoneChannels.channelsChanged(this);if(momentary()&&localOn)level.scheduleTick(worldPosition,getBlockState().getBlock(),20);changed();}
    @Override public void setRemoved(){ModernRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){ModernRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);writeMount(tag);if(powerSize>=0)tag.putInt("PowerLeverSize",powerSize);if(signalControl())new com.vandorlabs.persistence.ControlSettings.Detents(low,high,selectedStep).write(new NbtPrimitiveData(tag));new RedstoneData.Source(channels.first(),localOn,initialized,mountRotation).write(new NbtPrimitiveData(tag));tag.putIntArray("RedstoneChannels",channels.toArray());tag.putIntArray("LatchedChannels",latched.toArray());int[] saved=new int[channels.size()];for(int i=0;i<saved.length;i++)saved[i]=latchedLevel(channels.get(i));tag.putIntArray("LatchedLevels",saved);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);var source=RedstoneData.Source.read(new NbtPrimitiveData(tag));channels=ChannelMembershipData.read(tag.contains("RedstoneChannels",11)?tag.getIntArray("RedstoneChannels"):null,ChannelList.of(source.channel));localOn=source.localOn;initialized=source.initialized;mountRotation=source.mountRotation;
        latched=ChannelMembershipData.read(tag.contains("LatchedChannels",11)?tag.getIntArray("LatchedChannels"):null,localOn?channels:ChannelList.EMPTY).intersect(channels);levels.clear();int[] saved=tag.getIntArray("LatchedLevels");for(int i=0;i<Math.min(saved.length,channels.size());i++)levels.put(channels.get(i),Math.max(0,Math.min(15,saved[i])));
        powerSize=com.vandorlabs.persistence.ControlSettings.readSize(new NbtPrimitiveData(tag));if(signalControl()){var detents=com.vandorlabs.persistence.ControlSettings.Detents.read(new NbtPrimitiveData(tag),low,high);low=detents.low;high=detents.high;selectedStep=detents.step;}
        int h=tag.getInt("BaseHeight"),t=com.vandorlabs.render.ControlMount.migratedTilt(tag.getInt("BaseTilt"),tag.contains("ControlMountVersion")),d=tag.getInt("TiltDirection");var mount=com.vandorlabs.persistence.ControlSettings.Mount.read(new NbtPrimitiveData(tag),signalControl()?new com.vandorlabs.persistence.ControlSettings.Mount(0,0,0):new com.vandorlabs.persistence.ControlSettings.Mount(baseHeight,baseTilt,tiltDirection));baseHeight=mount.height;baseTilt=mount.tilt;tiltDirection=mount.direction;
        requestModelDataUpdate();
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveWithoutMetadata(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
