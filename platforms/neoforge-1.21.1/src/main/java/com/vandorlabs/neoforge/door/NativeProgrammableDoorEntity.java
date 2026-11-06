package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.persistence.RedstoneData;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;

/** Inactive native door storage/session host. Registration awaits actual linked-part, motion and reader lifecycle. */
public class NativeProgrammableDoorEntity extends BlockEntity implements com.vandorlabs.neoforge.redstone.NativeChannelMember {
    public record Visual(NativeDoorSettings settings,BlockState state,com.vandorlabs.render.SpaceDoorControlPanel.Side side,boolean large,boolean recovery){}
    public static final ModelProperty<NativeDoorSettings> SETTINGS=new ModelProperty<>();
    public static final ModelProperty<Visual> VISUAL=new ModelProperty<>();
    private volatile Visual visual;
    private boolean visualDirty=true;
    private BlockState lastRawState;
    private final boolean large;
    private volatile NativeDoorSettings settings;
    private CompoundTag retained=new CompoundTag();
    private ChannelList channels=ChannelList.EMPTY;
    private boolean channelSignal,securityToggled,legacyMotionPending;
    private boolean localDirty=true,membershipDirty;
    public NativeProgrammableDoorEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,boolean large,boolean slidingModel){super(type,pos,state);this.large=large;settings=NativeDoorSettings.fresh(large,slidingModel);visual=new Visual(settings,state,com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE,large,false);lastRawState=state;}
    public final boolean large(){return large;}
    public final NativeDoorSettings settings(){return settings;}
    final NativeDoorSettings placementSettings(){migrateSavedModel();return settings;}
    public final ChannelList getRedstoneChannels(){return channels;}
    public final boolean channelSignal(){return channelSignal;}
    public final boolean securityToggled(){return securityToggled;}
    public final boolean legacyMotionPending(){return legacyMotionPending;}
    @Override public ModelData getModelData(){var snapshot=visual;return ModelData.builder().with(SETTINGS,snapshot.settings()).with(VISUAL,snapshot).build();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);placementRecovery=tag.getBoolean("DoorPlacementRecovery");retained=tag.copy();for(String key:java.util.List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retained.remove(key);
        settings=NativeDoorSettings.read(tag);ChannelList fallback=ChannelList.of(Math.max(0,tag.getInt("RedstoneChannel")));
        try{channels=tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY)?ChannelList.of(tag.getIntArray("RedstoneChannels")):fallback;}catch(IllegalArgumentException malformed){channels=fallback;}
        channelSignal=tag.getBoolean("RedstoneSignal");securityToggled=tag.getBoolean("DoorSecurityToggled");legacyMotionPending=!tag.contains("SpaceDoorSchema");localDirty=true;visualDirty=true;publishLocalVisual();requestModelDataUpdate();if(level!=null&&!level.isClientSide&&getBlockState().getBlock() instanceof NativeProgrammableDoorBlock)com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(this);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.merge(retained);writePublicSettings(tag);tag.putBoolean("DoorSecurityToggled",securityToggled);if(placementRecovery)tag.putBoolean("DoorPlacementRecovery",true);else tag.remove("DoorPlacementRecovery");}
    private void writePublicSettings(CompoundTag tag){settings.write(tag);tag.putIntArray("RedstoneChannels",channels.toArray());new RedstoneData.Member(channels.first(),channelSignal).write(new ModernHousingTags(tag));}
    /** Explicit projection: opaque retained data, reader inventories and foreign capabilities never enter a menu/update packet. */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();writePublicSettings(tag);tag.putBoolean("DoorSecurityToggled",securityToggled);if(placementRecovery)tag.putBoolean("DoorPlacementRecovery",true);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    /** Configured items contain choices/channels only, matching legacy itemSettings rather than a world snapshot. */
    public final CompoundTag itemSettings(){CompoundTag tag=new CompoundTag();settings.write(tag);tag.putInt("SpaceDoorChannel",channels.first());tag.putIntArray("RedstoneChannels",channels.toArray());return tag;}
    private boolean placementRecovery;
    final void installPlacement(NativeDoorSettings next,ChannelList bank){settings=next;channels=bank;legacyMotionPending=false;membershipDirty=true;}
    final void retainPlacementRecovery(){placementRecovery=true;publishLocalVisual();setChanged();requestModelDataUpdate();}
    public final boolean placementRecovery(){return placementRecovery;}
    public static boolean validFrames(com.vandorlabs.persistence.FrameSettings f){return f!=null&&NativeDoorSettings.frame(f.front)&&NativeDoorSettings.surface(f.interior)&&NativeDoorSettings.surface(f.exterior)&&(f.artworkVersion==0||f.artworkVersion==1)&&(f.splitTop==-1||com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(f.splitTop))&&(f.splitBottom==-1||com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(f.splitBottom));}
    final boolean stageFrames(com.vandorlabs.persistence.FrameSettings frames){var old=settings;var next=new NativeDoorSettings(old.door(),old.faceTexture(),old.rawLayout(),old.tileTexture(),old.depth(),old.rawPanelMode(),old.securityMode(),old.securityAllowed(),frames,old.glass());if(old.same(next))return false;settings=next;return true;}
    final boolean stageGlass(NativeDoorGlass glass){if(settings.glass().same(glass))return false;var old=settings;settings=new NativeDoorSettings(old.door(),old.faceTexture(),old.rawLayout(),old.tileTexture(),old.depth(),old.rawPanelMode(),old.securityMode(),old.securityAllowed(),old.frames(),glass);return true;}
    final boolean accepts(NativeDoorEdit edit){return edit!=null&&edit.accepts(large);}
    final boolean stage(NativeDoorEdit edit,ChannelList next){
        NativeDoorSettings updated=edit.apply(settings);boolean changed=!settings.same(updated)||!channels.equals(next);
        if(!changed)return false;
        if(settings.securityMode()!=updated.securityMode()||settings.securityAllowed()!=updated.securityAllowed())securityToggled=false;
        int previousBehavior=NativeDoorSettings.securityBehavior(settings.securityMode());settings=updated;membershipDirty|=!channels.equals(next);channels=next;legacyMotionPending=false;
        for(String key:java.util.List.of("DoorKeyReader","DoorRearKeyReader"))if(retained.contains(key,Tag.TAG_COMPOUND)){
            CompoundTag reader=retained.getCompound(key);boolean any=updated.securityAllowed()==127;int behavior=NativeDoorSettings.securityBehavior(updated.securityMode());
            if(NativeDoorSettings.readerCompany(reader)!=updated.securityAllowed()||previousBehavior!=behavior){reader.putLong("TokenPulseUntil",0);reader.putBoolean("TokenToggled",false);}
            reader.putBoolean("AnyToken",any);reader.putInt("AllowedTokens",updated.securityAllowed());reader.putInt("TokenBehavior",behavior);
        }
        return true;
    }
    final void publish(){publishLocalVisual();refreshVisual(false);setChanged();requestModelDataUpdate();if(level!=null&&!level.isClientSide){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);if(membershipDirty&&isChannelMemberActive())com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.channelsChanged(this);membershipDirty=false;localInputChanged();}}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public boolean isChannelMemberActive(){return !placementRecovery&&com.vandorlabs.neoforge.redstone.NativeChannelMember.super.isChannelMemberActive()&&getBlockState().getBlock() instanceof NativeProgrammableDoorBlock block&&getBlockState().getValue(NativeProgrammableDoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER&&NativeDoorLifecycle.complete(level,worldPosition,block);}
    @Override public int localSignalLevel(int channel){return isChannelMemberActive()?NativeDoorLifecycle.localPower(level,worldPosition):0;}
    @Override public void setChannelLevel(int value){boolean next=value>0;if(channelSignal==next)return;channelSignal=next;NativeDoorLifecycle.updatePower(this);}
    public final void localInputChanged(){localDirty=true;if(level!=null&&!level.isClientSide)tickDoor();}
    private void publishLocalVisual(){visual=new Visual(settings,getBlockState(),visual==null?com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE:visual.side(),large,placementRecovery);lastRawState=getBlockState();}
    public final void markVisualDirty(){visualDirty=true;}
    public final void refreshVisual(){refreshVisual(true);}
    private void refreshVisual(boolean notify){
        visualDirty=false;lastRawState=getBlockState();if(level==null||NativeDoorLifecycle.root(level,worldPosition)!=this){publishLocalVisual();return;}
        var block=(NativeProgrammableDoorBlock)getBlockState().getBlock();var state=block.resolved(level,worldPosition,getBlockState());var side=NativeDoorLifecycle.panelSide(this,state);var old=visual;
        visualDirty=false;if(old.settings()==settings&&old.state()==state&&old.side()==side)return;visual=new Visual(settings,state,side,large,placementRecovery);if(notify)requestModelDataUpdate();
        if(notify&&level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    public final void tickDoor(){if(getBlockState()!=lastRawState){visualDirty=true;localDirty=true;}if(level==null)return;if(getBlockState().getBlock() instanceof NativeProgrammableDoorBlock&&getBlockState().getValue(NativeProgrammableDoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER){visualDirty=false;localDirty=false;lastRawState=getBlockState();return;}if(level.isClientSide){if(visualDirty)refreshVisual();return;}if(!visualDirty&&!localDirty)return;if(!isChannelMemberActive()){visualDirty=false;localDirty=false;lastRawState=getBlockState();return;}if(visualDirty)refreshVisual();if(placementRecovery||!localDirty)return;localDirty=false;migrateSavedModel();NativeDoorLifecycle.updatePower(this);}
    @Override public void beforeChannelRegistration(){migrateSavedModel();localDirty=true;tickDoor();}
    private void migrateSavedModel(){if(!legacyMotionPending||!(getBlockState().getBlock() instanceof NativeProgrammableDoorBlock block))return;var old=settings;var data=old.door();boolean sliding=block.slidingModel();var migrated=new com.vandorlabs.persistence.SpaceDoorData(data.design,data.detail,data.framed,data.direction,sliding||data.middle,sliding,data.hinges,data.trigger,data.panel);settings=new NativeDoorSettings(migrated,old.faceTexture(),old.rawLayout(),old.tileTexture(),sliding?0:old.depth(),old.rawPanelMode(),old.securityMode(),old.securityAllowed(),old.frames(),old.glass());legacyMotionPending=false;publishLocalVisual();visualDirty=true;if(level!=null&&!level.isClientSide)setChanged();}
    @Override public void onLoad(){super.onLoad();localDirty=true;visualDirty=true;if(level!=null){if(level.isClientSide)migrateSavedModel();else if(getBlockState().getBlock() instanceof NativeProgrammableDoorBlock&&getBlockState().getValue(NativeProgrammableDoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(this);}}
    @Override public void setRemoved(){com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
}
