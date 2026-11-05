package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.neoforge.redstone.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;

/** Current mounted rocker/button state; runtime latch data is never copied into ordinary items. */
public final class NativeBinaryControlBlockEntity extends BlockEntity implements NativeChannelLatch,ControlLatchData.LevelLookup {
    public record Visual(ControlMountSettings mount,int rotation){}
    public static final ModelProperty<Visual> VISUAL=new ModelProperty<>();
    private ChannelList channels=ChannelList.EMPTY,latched=ChannelList.EMPTY,transmission=ChannelList.EMPTY;
    private final Map<Integer,Integer> levels=new HashMap<>();
    private boolean localOn,initialized,local=true,registrationPending=true;
    private int rotation,range=16;
    private ControlMountSettings mount=ControlMountSettings.of(0,0,0);
    private volatile Visual visual=new Visual(mount,0);
    private CompoundTag retained=new CompoundTag();
    public NativeBinaryControlBlockEntity(BlockPos pos,BlockState state){super(VandorLabsBootstrap.BINARY_CONTROL_ENTITY.get(),pos,state);}
    @Override public boolean isChannelMemberActive(){return getBlockState().getBlock() instanceof NativeMountedControl&&NativeChannelLatch.super.isChannelMemberActive();}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public ChannelList transmittedChannels(){return transmission;}
    public ChannelList transmissionChannels(){return transmission;}
    @Override public int transmissionRange(){return range;}
    public boolean localRedstone(){return local;}
    public ControlMountSettings mountSettings(){return mount;}
    public int mountRotation(){return rotation;}
    public boolean isLocalOn(){return localOn;}
    @Override public boolean latchOn(){return localOn;}
    @Override public ChannelList latchedChannels(){return latched;}
    @Override public int latchedLevel(int channel){return ControlLatchData.level(levels,latched,channel);}
    @Override public int localSignalLevel(int channel){return ControlLatchData.localLevel(isChannelLatch(),localOn,this,channel);}
    public int getOutputLevel(){return ControlLatchData.outputLevel(localOn,channels,this);}
    @Override public boolean isChannelLatch(){return getBlockState().getBlock() instanceof NativeMountedControl block&&!block.isMomentary();}
    @Override public void setChannelLevel(int ignored){} // Received aggregate never becomes an independent local source.
    private void changed(){
        setChanged();visual=new Visual(mount,rotation);requestModelDataUpdate();
        if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    private boolean linkedState(){
        if(level==null||level.isClientSide||!(getBlockState().getBlock() instanceof NativeMountedControl block)||block.isMomentary())return false;
        BlockState state=getBlockState();if(block.powered(state)==localOn)return false;
        level.setBlock(worldPosition,block.withPowered(state,localOn),2);return true;
    }
    public void notifyPhysical(){if(NativeSignalUpdateBatch.isActive())NativeSignalUpdateBatch.afterSignals(this,this::emitPhysical);else NativeSignalUpdateBatch.apply(()->NativeSignalUpdateBatch.afterSignals(this,this::emitPhysical));}
    private void emitPhysical(){
        if(level==null||level.isClientSide||!isChannelMemberActive())return;
        var block=getBlockState().getBlock();
        for(Direction face:Direction.values()){
            BlockPos neighbor=worldPosition.relative(face);if(!level.hasChunkAt(neighbor))continue;level.neighborChanged(neighbor,block,worldPosition);
            if(level.getBlockState(neighbor).isRedstoneConductor(level,neighbor))for(Direction side:Direction.values()){BlockPos target=neighbor.relative(side);if(level.hasChunkAt(target))level.neighborChanged(target,block,neighbor);}
        }
    }
    @Override public void applyLinkedLatch(boolean on){applyLinkedChannels(on?channels:ChannelList.EMPTY);}
    private boolean updateLinkedChannels(ChannelList active){
        if(level==null||level.isClientSide||!isChannelLatch())return false;
        int before=getOutputLevel();ChannelList next=active.intersect(channels);boolean dirty=!latched.equals(next);latched=next;boolean on=!channels.isEmpty()&&latched.containsAll(channels);
        if(localOn!=on||!initialized||dirty){localOn=on;initialized=true;changed();}boolean repaired=linkedState();return repaired||before!=getOutputLevel();
    }
    @Override public void applyLinkedChannels(ChannelList active){if(updateLinkedChannels(active))notifyPhysical();}
    @Override public void applyLinkedLevels(Map<Integer,Integer> next){
        int previous=getOutputLevel();boolean dirty=!levels.equals(next);levels.clear();levels.putAll(next);boolean physical=updateLinkedChannels(NativeChannelLatch.activeChannels(next));
        if(dirty)changed();if(physical||previous!=getOutputLevel())notifyPhysical();
    }
    public void setLocalOn(boolean value){
        if(localOn==value&&initialized&&(value||latched.isEmpty()))return;
        levels.clear();localOn=value;latched=value?channels:ChannelList.EMPTY;initialized=true;changed();
        if(level!=null&&!level.isClientSide){if(isChannelLatch()&&!channels.isEmpty())NativeRedstoneChannels.latchLevelChanged(this,value?15:0);else NativeRedstoneChannels.outputChanged(this);}
    }
    private void setChannels(ChannelList next){
        if(channels.equals(next))return;latched=localOn?next:latched.intersect(next);channels=next;changed();
    }
    public boolean configureMount(ControlMountSettings next){if(next==null||level!=null&&!(getBlockState().getBlock() instanceof NativeMountedControl))return false;mount=next;changed();return true;}
    public boolean configureTransmission(ChannelList next,int radius,boolean nativeOutput){
        if(!TransmissionSettings.validConfiguration(next,radius))return false;
        if(next.equals(transmission)&&radius==range&&nativeOutput==local&&next.equals(channels))return true;
        Runnable apply=()->{
            if(level instanceof ServerLevel)NativeRedstoneChannels.unregister(this);transmission=next;range=radius;boolean before=local;local=nativeOutput;
            CompoundTag forge=retained.getCompound("ForgeData"),all=forge.getCompound(TransmissionSettings.SETTINGS),settings=new CompoundTag();settings.putIntArray("Channels",next.toArray());settings.putInt("Range",radius);settings.putBoolean("Local",nativeOutput);all.put("main",settings);forge.put(TransmissionSettings.SETTINGS,all);retained.put("ForgeData",forge);
            setChannels(next);if(level instanceof ServerLevel)NativeRedstoneChannels.channelsChanged(this);changed();if(before!=local)notifyPhysical();
        };
        if(level instanceof ServerLevel server)NativeRedstoneChannels.batch(server,apply);else apply.run();
        return true;
    }
    /** Only configured item fields; no initialized/on/latched/range/Local runtime state. */
    public CompoundTag configuration(){var result=new CompoundTag();result.putInt("Channel",channels.first());result.putIntArray("RedstoneChannels",channels.toArray());ControlMountSettings.write(new ModernHousingTags(result),mount.height,mount.tilt,mount.direction);return result;}
    public void place(CompoundTag settings,int ownRotation){
        CompoundTag forge=retained.getCompound("ForgeData"),all=forge.getCompound(TransmissionSettings.SETTINGS),main=all.getCompound("main");
        main.remove("Channels");main.remove("Range");main.remove("Local");if(main.isEmpty())all.remove("main");else all.put("main",main);if(all.isEmpty())forge.remove(TransmissionSettings.SETTINGS);else forge.put(TransmissionSettings.SETTINGS,all);if(forge.isEmpty())retained.remove("ForgeData");else retained.put("ForgeData",forge);
        channels=readChannels(settings,"RedstoneChannels",ChannelList.of(Math.max(0,settings.getInt("Channel"))));transmission=channels;range=16;local=true;
        levels.clear();latched=ChannelList.EMPTY;localOn=false;initialized=true;rotation=Math.floorMod(ownRotation,4);mount=ControlMountSettings.read(new ModernHousingTags(settings),0,0,0);registrationPending=false;
        changed();if(level!=null&&!level.isClientSide)NativeRedstoneChannels.channelsChanged(this);
    }
    @Override public void beforeChannelRegistration(){
        if(!registrationPending||!(level instanceof ServerLevel server))return;registrationPending=false;
        BlockState state=server.getBlockState(worldPosition);if(!(state.getBlock() instanceof NativeMountedControl block))return;
        BlockPos support=worldPosition.relative(block.supportDirection(state));
        if(server.hasChunkAt(support)&&!state.canSurvive(server,worldPosition)){block.dropUnsupported(state,server,worldPosition);return;}
        if(!initialized){localOn=block.powered(state);latched=localOn?channels:ChannelList.EMPTY;initialized=true;setChanged();}
        if(block.isMomentary()&&block.powered(state))server.scheduleTick(worldPosition,state.getBlock(),20);
    }
    @Override public void onLoad(){super.onLoad();registrationPending=true;if(level!=null&&!level.isClientSide)NativeRedstoneChannels.queueRegistration(this);}
    @Override public void setRemoved(){NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    private static ChannelList readChannels(CompoundTag tag,String key,ChannelList fallback){try{return tag.contains(key,Tag.TAG_INT_ARRAY)?ChannelList.of(tag.getIntArray(key)):fallback;}catch(IllegalArgumentException malformed){return fallback;}}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);retained=tag.copy();for(String key:List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retained.remove(key);
        var primitive=new ModernHousingTags(tag);var data=RedstoneData.Source.read(primitive);channels=readChannels(tag,"RedstoneChannels",ChannelList.of(data.channel));localOn=data.localOn;initialized=data.initialized;rotation=data.mountRotation;
        mount=ControlMountSettings.read(primitive,mount.height,mount.tilt,mount.direction);latched=ControlLatchData.read(primitive,channels,localOn,levels);
        CompoundTag configured=tag.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");
        transmission=TransmissionSettings.readChannels(configured.contains("Channels",Tag.TAG_INT_ARRAY),configured.getIntArray("Channels"),channels);range=TransmissionSettings.readRange(configured.contains("Range",Tag.TAG_INT),configured.getInt("Range"),true);local=TransmissionSettings.readLocal(configured.contains("Local",Tag.TAG_BYTE),configured.getBoolean("Local"),true);
        registrationPending=true;visual=new Visual(mount,rotation);requestModelDataUpdate();
        if(level!=null){if(level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);else NativeRedstoneChannels.queueRegistration(this);}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.merge(retained);var primitive=new ModernHousingTags(tag);new RedstoneData.Source(channels.first(),localOn,initialized,rotation).write(primitive);tag.putIntArray("RedstoneChannels",channels.toArray());ControlMountSettings.write(primitive,mount.height,mount.tilt,mount.direction);ControlLatchData.write(primitive,channels,latched,this);}
    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components){super.collectImplicitComponents(components);CompoundTag wrapper=new CompoundTag();wrapper.put("RedstoneChannelSettings",configuration());components.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(wrapper));}
    @Override protected void applyImplicitComponents(DataComponentInput input){
        super.applyImplicitComponents(input);var data=input.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(data==null)return;
        CompoundTag wrapper=data.copyTag();if(!wrapper.contains("RedstoneChannelSettings",Tag.TAG_COMPOUND))return;
        CompoundTag settings=wrapper.getCompound("RedstoneChannelSettings");channels=readChannels(settings,"RedstoneChannels",ChannelList.of(Math.max(0,settings.getInt("Channel"))));transmission=channels;mount=ControlMountSettings.read(new ModernHousingTags(settings),0,0,0);visual=new Visual(mount,rotation);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveWithoutMetadata(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public ModelData getModelData(){return ModelData.builder().with(VISUAL,visual).build();}
}
