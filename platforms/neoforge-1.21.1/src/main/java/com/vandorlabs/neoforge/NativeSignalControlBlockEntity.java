package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.neoforge.redstone.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Four detents use the mounted host's numeric latch bank, transmission and opaque save data. */
public final class NativeSignalControlBlockEntity extends NativeBinaryControlBlockEntity {
    private int low=5,high=15,step;
    public NativeSignalControlBlockEntity(BlockPos pos,BlockState state){super(VandorLabsBootstrap.SIGNAL_CONTROL_ENTITY.get(),pos,state);}
    @Override public boolean supportsSignalControl(){return getBlockState().getBlock() instanceof NativeSignalControlBlock;}
    @Override public boolean isChannelMemberActive(){return supportsSignalControl()&&super.isChannelMemberActive();}
    @Override public boolean isSelectableControlType(){return getBlockState().getBlock() instanceof NativeSignalControlBlock block&&block.hasSelectableType();}
    @Override public int getControlType(){return getBlockState().getBlock() instanceof NativeSignalControlBlock block?block.controlType():0;}
    @Override public int getLowLimit(){return low;}
    @Override public int getHighLimit(){return high;}
    public int getStep(){return getRedstoneChannels().isEmpty()?step:SignalControlData.stepForLevel(getOutputLevel(),low,high);}
    @Override public int getOutputLevel(){return getRedstoneChannels().isEmpty()?SignalControlData.levelForStep(step,low,high):super.getOutputLevel();}
    public void setStep(int value){
        if(value<0||value>3)return;
        if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Signal control changes require the owning server thread");
        NativeSignalUpdateBatch.apply(()->{step=value;if(getRedstoneChannels().isEmpty())super.setLocalOn(value>0);else NativeRedstoneChannels.latchLevelChanged(this,SignalControlData.levelForStep(value,low,high));linkedState();changed();notifyPhysical();});
    }
    @Override public void setLocalOn(boolean value){setStep(value?3:0);}
    @Override protected boolean linkedState(){
        if(level==null||level.isClientSide||!(getBlockState().getBlock() instanceof NativeSignalControlBlock))return false;
        BlockState before=getBlockState(),after=before.setValue(NativeSignalControlBlock.ON,getOutputLevel()>0).setValue(NativeSignalControlBlock.LEVEL,getStep());
        if(before==after)return false;level.setBlock(worldPosition,after,2);return before.getValue(NativeSignalControlBlock.ON)!=after.getValue(NativeSignalControlBlock.ON);
    }
    @Override protected ControlMountSettings readSavedMount(ModernHousingTags tag){return ControlMountSettings.read(tag,0,0,0);}
    @Override protected void transmissionChannelsChanged(ChannelList previous,int previousOutput){
        int selected=previous.isEmpty()?step:SignalControlData.stepForLevel(previousOutput,low,high);
        if(getRedstoneChannels().isEmpty())setStep(selected);else if(previousOutput>0)NativeRedstoneChannels.latchLevelChanged(this,previousOutput);
    }
    /** Wire bounds are stricter than the historical saved-data predicate's overflow edge. */
    public static boolean validWireLimits(int low,int high){return low>=1&&low<=15&&high>=1&&high<=15&&SignalControlData.validLimits(low,high);}
    public boolean configureSignal(ControlMountSettings mount,int nextLow,int nextHigh,int type){
        if(!supportsSignalControl()||mount==null||!ControlMountSettings.valid(mount.height,mount.tilt,mount.direction)||!validWireLimits(nextLow,nextHigh)||(isSelectableControlType()?type<0||type>2:type!=-1))return false;
        Runnable apply=()->{int selected=getStep();setMountValue(mount);low=nextLow;high=nextHigh;setStep(selected);if(isSelectableControlType())configureType(type);};
        if(level instanceof ServerLevel server)NativeRedstoneChannels.batch(server,apply);else apply.run();return true;
    }
    public void configureType(int type){
        if(type<0||type>2||!isSelectableControlType()||!(level instanceof ServerLevel server))return;
        if(!server.getServer().isSameThread())throw new IllegalStateException("Signal type changes require the owning server thread");
        var next=NativeSignalControlBlock.selectable(type);BlockState before=getBlockState();if(next==before.getBlock())return;
        level.setBlock(worldPosition,next.defaultBlockState().setValue(NativeSignalControlBlock.FACING,before.getValue(NativeSignalControlBlock.FACING)).setValue(NativeSignalControlBlock.ROTATION,before.getValue(NativeSignalControlBlock.ROTATION)).setValue(NativeSignalControlBlock.ON,before.getValue(NativeSignalControlBlock.ON)).setValue(NativeSignalControlBlock.LEVEL,getStep()),2);changed();notifyPhysical();
    }
    @Override public CompoundTag configuration(){var tag=super.configuration();tag.remove("Channel");if(isSelectableControlType())tag.putInt("ControlType",getControlType());tag.putInt(SignalControlData.LOW,low);tag.putInt(SignalControlData.HIGH,high);return tag;}
    private void readItemSettings(CompoundTag tag){
        int l=tag.getInt(SignalControlData.LOW),h=tag.getInt(SignalControlData.HIGH);if(SignalControlData.validLimits(l,h)){low=l;high=h;}
        if(tag.contains("ControlType",Tag.TAG_INT))configureType(tag.getInt("ControlType"));
    }
    @Override public void place(CompoundTag tag,int rotation){step=0;readItemSettings(tag);super.place(tag,rotation);linkedState();}
    @Override protected void applyImplicitComponents(DataComponentInput input){super.applyImplicitComponents(input);var data=input.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(data==null)return;var tag=data.copyTag();if(tag.contains("RedstoneChannelSettings",Tag.TAG_COMPOUND))readItemSettings(tag.getCompound("RedstoneChannelSettings"));}
    @Override public void beforeChannelRegistration(){super.beforeChannelRegistration();if(level instanceof ServerLevel&&isChannelMemberActive()){var before=getBlockState();var after=before.setValue(NativeSignalControlBlock.LEVEL,getStep());if(before!=after)level.setBlock(worldPosition,after,2);}}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);int l=SignalControlData.savedLow(tag.contains(SignalControlData.LOW),tag.getInt(SignalControlData.LOW)),h=SignalControlData.savedHigh(tag.contains(SignalControlData.HIGH),tag.getInt(SignalControlData.HIGH));if(SignalControlData.validLimits(l,h)){low=l;high=h;}step=SignalControlData.savedStep(tag.getInt(SignalControlData.STEP));}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putInt(SignalControlData.LOW,low);tag.putInt(SignalControlData.HIGH,high);tag.putInt(SignalControlData.STEP,step);}
}
