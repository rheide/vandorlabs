package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.fluid.*;
import com.vandorlabs.persistence.RedstoneData;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;

/** Inactive native door storage/session host. Registration awaits actual linked-part, motion and reader lifecycle. */
public class NativeProgrammableDoorEntity extends BlockEntity implements com.vandorlabs.neoforge.redstone.NativeChannelMember, ContainedFluidHost {
    public record Visual(NativeDoorSettings settings,BlockState state,com.vandorlabs.render.SpaceDoorControlPanel.Side side,boolean large,boolean recovery){}
    public static final ModelProperty<NativeDoorSettings> SETTINGS=new ModelProperty<>();
    public static final ModelProperty<Visual> VISUAL=new ModelProperty<>();
    private volatile Visual visual;
    private final NativeFluidSlots fluids=new NativeFluidSlots();
    private static final NativeFluidVolume NO_FLUID_VOLUME=NativeFluidVolume.build(java.util.List.of(new net.minecraft.world.phys.AABB(0,0,0,1,1,1)),NativeFluidVolume.SINGLE_SIDE);
    private record FluidKey(boolean sliding,boolean framed,boolean hinges,int depth,Direction face,boolean open,boolean left,boolean paired){}
    private record FluidGeometry(FluidKey key,NativeDoorFluidGeometry.Plane plane,NativeFluidVolume volume,long revision){}
    private volatile FluidGeometry fluidGeometry=new FluidGeometry(null,null,NO_FLUID_VOLUME,0);
    private NativeFluidSlots.Snapshot effectiveBase;
    private volatile NativeFluidSlots.Snapshot effectiveFluids=fluids.snapshot();
    private int effectiveWater=-1;private long effectiveRevision;
    private volatile NativeFluidRenderSnapshot fluidRender=NativeFluidRenderSnapshot.EMPTY;
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
    @Override public ModelData getModelData(){var snapshot=visual;return ModelData.builder().with(SETTINGS,snapshot.settings()).with(VISUAL,snapshot).with(NativeFluidRenderSnapshot.DATA,fluidRender).build();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);fluids.read(tag,registries);NativeFluidWorld.queue(this);placementRecovery=tag.getBoolean("DoorPlacementRecovery");retained=tag.copy();for(String key:java.util.List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retained.remove(key);
        settings=NativeDoorSettings.read(tag);ChannelList fallback=ChannelList.of(Math.max(0,tag.getInt("RedstoneChannel")));
        try{channels=tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY)?ChannelList.of(tag.getIntArray("RedstoneChannels")):fallback;}catch(IllegalArgumentException malformed){channels=fallback;}
        channelSignal=tag.getBoolean("RedstoneSignal");securityToggled=tag.getBoolean("DoorSecurityToggled");legacyMotionPending=!tag.contains("SpaceDoorSchema");localDirty=true;visualDirty=true;publishLocalVisual();requestModelDataUpdate();if(level!=null&&!level.isClientSide&&getBlockState().getBlock() instanceof NativeProgrammableDoorBlock)com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(this);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.merge(retained);fluids.write(tag);writePublicSettings(tag);tag.putBoolean("DoorSecurityToggled",securityToggled);if(placementRecovery)tag.putBoolean("DoorPlacementRecovery",true);else tag.remove("DoorPlacementRecovery");}
    private void writePublicSettings(CompoundTag tag){settings.write(tag);tag.putIntArray("RedstoneChannels",channels.toArray());new RedstoneData.Member(channels.first(),channelSignal).write(new ModernHousingTags(tag));}
    /** Explicit projection: opaque retained data, reader inventories and foreign capabilities never enter a menu/update packet. */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();writePublicSettings(tag);fluids.write(tag);tag.putBoolean("DoorSecurityToggled",securityToggled);if(placementRecovery)tag.putBoolean("DoorPlacementRecovery",true);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    /** Configured items contain choices/channels only, matching legacy itemSettings rather than a world snapshot. */
    public final CompoundTag itemSettings(){CompoundTag tag=new CompoundTag();settings.write(tag);tag.putInt("SpaceDoorChannel",channels.first());tag.putIntArray("RedstoneChannels",channels.toArray());return tag;}
    private boolean placementRecovery;
    final void installPlacement(NativeDoorSettings next,ChannelList bank){settings=next;channels=bank;legacyMotionPending=false;membershipDirty=true;}
    final void retainPlacementRecovery(){placementRecovery=true;publishLocalVisual();setChanged();requestModelDataUpdate();}
    public final boolean placementRecovery(){return placementRecovery;}
    public static boolean validFrames(com.vandorlabs.persistence.FrameSettings f){return f!=null&&NativeDoorSettings.frame(f.front)&&NativeDoorSettings.surface(f.interior)&&NativeDoorSettings.surface(f.exterior)&&(f.artworkVersion==0||f.artworkVersion==1)&&(f.splitTop==-1||com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(f.splitTop))&&(f.splitBottom==-1||com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(f.splitBottom));}
    final boolean stageFrames(com.vandorlabs.persistence.FrameSettings frames){var old=settings;var next=new NativeDoorSettings(old.door(),old.faceTexture(),old.rawLayout(),old.tileTexture(),old.depth(),old.rawPanelMode(),old.securityMode(),old.securityAllowed(),frames,old.glass());if(old.same(next))return false;settings=next;refreshVisual(false);return true;}
    final boolean stageGlass(NativeDoorGlass glass){if(settings.glass().same(glass))return false;var old=settings;settings=new NativeDoorSettings(old.door(),old.faceTexture(),old.rawLayout(),old.tileTexture(),old.depth(),old.rawPanelMode(),old.securityMode(),old.securityAllowed(),old.frames(),glass);refreshVisual(false);return true;}
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
        refreshVisual(false);return true;
    }
    final void publish(){queueFluidParts();refreshVisual(false);setChanged();requestModelDataUpdate();if(level!=null&&!level.isClientSide){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);if(membershipDirty&&isChannelMemberActive())com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.channelsChanged(this);membershipDirty=false;localInputChanged();}}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public boolean isChannelMemberActive(){return !placementRecovery&&(!(level instanceof net.minecraft.server.level.ServerLevel server)||com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))&&com.vandorlabs.neoforge.redstone.NativeChannelMember.super.isChannelMemberActive()&&getBlockState().getBlock() instanceof NativeProgrammableDoorBlock block&&getBlockState().getValue(NativeProgrammableDoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER&&NativeDoorLifecycle.complete(level,worldPosition,block);}
    @Override public int localSignalLevel(int channel){return isChannelMemberActive()?NativeDoorLifecycle.localPower(level,worldPosition):0;}
    @Override public void setChannelLevel(int value){boolean next=value>0;if(channelSignal==next)return;channelSignal=next;NativeDoorLifecycle.updatePower(this);}
    public final void localInputChanged(){localDirty=true;if(level!=null&&!level.isClientSide)tickDoor();}
    private void publishLocalVisual(){visual=new Visual(settings,getBlockState(),visual==null?com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE:visual.side(),large,placementRecovery);lastRawState=getBlockState();}
    public final void markVisualDirty(){visualDirty=true;}
    public final void refreshVisual(){refreshVisual(true);}
    private void refreshVisual(boolean notify){
        visualDirty=false;lastRawState=getBlockState();if(level==null||NativeDoorLifecycle.root(level,worldPosition)!=this){if(visual.settings()!=settings||visual.state()!=getBlockState()||visual.recovery()!=placementRecovery)publishLocalVisual();return;}
        var block=(NativeProgrammableDoorBlock)getBlockState().getBlock();var state=block.resolved(level,worldPosition,getBlockState());var side=NativeDoorLifecycle.panelSide(this,state);var old=visual;
        visualDirty=false;if(old.settings()==settings&&old.state()==state&&old.side()==side)return;visual=new Visual(settings,state,side,large,placementRecovery);if(notify)requestModelDataUpdate();
        if(notify&&level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    public final void tickDoor(){if(getBlockState()!=lastRawState){visualDirty=true;localDirty=true;NativeFluidWorld.queue(this);}if(level==null)return;if(getBlockState().getBlock() instanceof NativeProgrammableDoorBlock&&getBlockState().getValue(NativeProgrammableDoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER){visualDirty=false;localDirty=false;lastRawState=getBlockState();return;}if(level.isClientSide){if(visualDirty)refreshVisual();return;}if(!visualDirty&&!localDirty)return;if(!isChannelMemberActive()){visualDirty=false;localDirty=false;lastRawState=getBlockState();return;}if(visualDirty)refreshVisual();if(placementRecovery||!localDirty)return;localDirty=false;migrateSavedModel();NativeDoorLifecycle.updatePower(this);}
    @Override public void beforeChannelRegistration(){migrateSavedModel();localDirty=true;tickDoor();}
    private void migrateSavedModel(){if(!legacyMotionPending||!(getBlockState().getBlock() instanceof NativeProgrammableDoorBlock block))return;var old=settings;var data=old.door();boolean sliding=block.slidingModel();var migrated=new com.vandorlabs.persistence.SpaceDoorData(data.design,data.detail,data.framed,data.direction,sliding||data.middle,sliding,data.hinges,data.trigger,data.panel);settings=new NativeDoorSettings(migrated,old.faceTexture(),old.rawLayout(),old.tileTexture(),sliding?0:old.depth(),old.rawPanelMode(),old.securityMode(),old.securityAllowed(),old.frames(),old.glass());legacyMotionPending=false;publishLocalVisual();visualDirty=true;if(level!=null&&!level.isClientSide)setChanged();}
    @Override public void onLoad(){super.onLoad();NativeFluidWorld.queue(this);localDirty=true;visualDirty=true;if(level!=null){if(level.isClientSide)migrateSavedModel();else if(getBlockState().getBlock() instanceof NativeProgrammableDoorBlock&&getBlockState().getValue(NativeProgrammableDoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(this);}}
    @Override public void setRemoved(){NativeFluidWorld.removed(this);com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeFluidWorld.unloaded(this);com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    /** Per-part slots are intentionally independent, including the upper part of a linked door. */
    @Override public BlockEntity fluidOwner(){return this;}
    @Override public NativeFluidSlots containedFluids(){return fluids;}
    @Override public NativeFluidSlots.Snapshot effectiveFluidSnapshot(){return effectiveFluids;}
    @Override public NativeFluidVolume fluidVolumeSnapshot(){return fluidGeometry.volume();}
    @Override public long fluidGeometryRevision(){return fluidGeometry.revision();}
    @Override public NativeFluidRenderSnapshot fluidRenderSnapshot(){return fluidRender;}
    @Override public void publishFluidRenderSnapshot(NativeFluidRenderSnapshot snapshot){fluidRender=java.util.Objects.requireNonNull(snapshot);}
    private static int frameLight(int choice){if(choice<0)return 0;var frame=com.vandorlabs.neoforge.ModernFinishCatalog.option(choice).frame();return frame==null?0:Math.max(0,Math.min(5,frame.light()));}
    @Override public int intrinsicContainedLight(){var profile=settings;if(!profile.door().framed)return 0;var frame=profile.frames();return Math.max(frame.bare?0:frameLight(frame.front),Math.max(frameLight(frame.interior),frameLight(frame.exteriorChoice())));}
    @Override public boolean openingFluidVolume(){return fluidGeometry.key()!=null;}
    @Override public int containedFluidSide(double x,double y,double z){var plane=fluidGeometry.plane();return plane==null?0:plane.side(x,y,z);}
    @Override public Direction[] fluidPlacementFaces(){var key=fluidGeometry.key();return key==null?new Direction[0]:new Direction[]{key.face(),key.face().getOpposite()};}
    @Override public void containedFluidsChanged(){setChanged();requestModelDataUpdate();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    /** Rebuild only on the owning thread, then publish one complete world-free geometry record. */
    @Override public void refreshFluidGeometry(){
        var old=fluidGeometry;var root=level==null?null:NativeDoorLifecycle.root(level,worldPosition);
        if(root==null||root.large()){if(old.key()!=null)fluidGeometry=new FluidGeometry(null,null,NO_FLUID_VOLUME,old.revision()+1);publishEffectiveFluids(0);return;}
        var state=NativeDoorLifecycle.resolved(level,worldPosition,getBlockState());var profile=root.settings();var door=profile.door();
        var key=new FluidKey(door.sliding,door.framed,door.hinges,profile.depth(),state.getValue(NativeProgrammableDoorBlock.FACING),state.getValue(NativeProgrammableDoorBlock.OPEN),state.getValue(NativeProgrammableDoorBlock.HINGE)==net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT,state.getValue(NativeProgrammableDoorBlock.PAIRED));
        int water=NativeDoorOpeningFluids.sources(level,worldPosition,key.face(),root.getBlockState().getValue(NativeProgrammableDoorBlock.OPEN),fluids.snapshot());publishEffectiveFluids(water);
        if(key.equals(old.key()))return;
        var built=NativeDoorFluidGeometry.build(profile,key.face(),key.open(),key.left(),key.paired(),worldPosition.getX(),worldPosition.getY(),worldPosition.getZ());
        fluidGeometry=new FluidGeometry(key,built.plane(),built.volume(),old.revision()+1);
    }
    private void publishEffectiveFluids(int water){var stored=fluids.snapshot();if(effectiveBase!=null&&stored.revision==effectiveBase.revision&&water==effectiveWater)return;effectiveBase=stored;effectiveWater=water;effectiveFluids=stored.withOpeningWater(water,++effectiveRevision);}
    private void queueFluidParts(){NativeFluidWorld.queue(this);if(level!=null){BlockPos lower=NativeDoorLifecycle.lower(getBlockState(),worldPosition);NativeFluidWorld.queueAt(level,lower);NativeFluidWorld.queueAt(level,lower.above());}}

}
