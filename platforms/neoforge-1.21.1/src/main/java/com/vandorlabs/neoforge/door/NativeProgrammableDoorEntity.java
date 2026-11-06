package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.neoforge.fluid.*;
import com.vandorlabs.persistence.RedstoneData;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;

/** Shared native Door storage, session and authenticated control host. */
public class NativeProgrammableDoorEntity extends BlockEntity implements com.vandorlabs.neoforge.redstone.NativeChannelMember, ContainedFluidHost {
    public record Visual(NativeDoorSettings settings,BlockState state,com.vandorlabs.render.SpaceDoorControlPanel.Side side,boolean large,boolean recovery,NativeDoorCredentialService.Projection readers){
        public Visual(NativeDoorSettings settings,BlockState state,com.vandorlabs.render.SpaceDoorControlPanel.Side side,boolean large,boolean recovery){this(settings,state,side,large,recovery,NativeDoorCredentialService.EMPTY);}
    }
    public static final ModelProperty<NativeDoorSettings> SETTINGS=new ModelProperty<>();
    public static final ModelProperty<Visual> VISUAL=new ModelProperty<>();
    private volatile Visual visual;
    private final NativeDoorCredentialService credentials;
    private volatile NativeDoorCredentialService.Projection readerProjection=NativeDoorCredentialService.EMPTY;
    private long nextCredentialTick=Long.MAX_VALUE;
    private boolean receivingProjection;
    private net.minecraft.world.item.ItemStack removalDrop=net.minecraft.world.item.ItemStack.EMPTY,creativeRecoveryDrop=net.minecraft.world.item.ItemStack.EMPTY;
    final void prepareRemovalDrop(net.minecraft.world.item.ItemStack stack){removalDrop=stack.copy();}
    final net.minecraft.world.item.ItemStack removalDropCopy(){return removalDrop.copy();}
    final void prepareCreativeRecoveryDrop(net.minecraft.world.item.ItemStack stack){creativeRecoveryDrop=stack.copy();}
    final net.minecraft.world.item.ItemStack takeCreativeRecoveryDrop(){var result=creativeRecoveryDrop;creativeRecoveryDrop=net.minecraft.world.item.ItemStack.EMPTY;return result;}
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
    private int waterOutlets;private boolean waterGushUpdating,waterGushDirty=true;private long nextWaterGushTick=Long.MAX_VALUE;
    protected final void markWaterGushDirty(){waterGushDirty=true;}
    final void scheduleWaterGush(boolean active){waterGushDirty=false;nextWaterGushTick=active&&level!=null?level.getGameTime()+5:Long.MAX_VALUE;}
    final boolean beginWaterGush(){if(waterGushUpdating)return false;waterGushUpdating=true;return true;}
    final void endWaterGush(){waterGushUpdating=false;}
    public final int waterOutlets(){return waterOutlets;}
    final void setWaterOutlets(int mask){if(waterOutlets!=(mask&255)){waterOutlets=mask&255;setChanged();}}
    private boolean localDirty=true,membershipDirty;
    public NativeProgrammableDoorEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,boolean large,boolean slidingModel){super(type,pos,state);this.large=large;settings=NativeDoorSettings.fresh(large,slidingModel);
        credentials=new NativeDoorCredentialService(new NativeDoorCredentialService.Host(){
            public BlockEntity owner(){return NativeProgrammableDoorEntity.this;}
            public boolean usable(net.minecraft.server.level.ServerPlayer player){return readerUsable(player);}
            public void changed(){credentialsChanged();}
            public void schedule(int ticks){if(level instanceof net.minecraft.server.level.ServerLevel server)nextCredentialTick=Math.min(nextCredentialTick,server.getGameTime()+Math.max(1,ticks));}
            public boolean securityToggled(){return securityToggled;}
            public void flipSecurityToggle(){flipCredentialToggle();}
        });
        visual=new Visual(settings,state,com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE,large,false,readerProjection);lastRawState=state;}
    public final Visual doorVisual(){return visual;}
    public final boolean large(){return large;}
    public final NativeDoorSettings settings(){return settings;}
    final NativeDoorSettings placementSettings(){migrateSavedModel();return settings;}
    public final ChannelList getRedstoneChannels(){return channels;}
    public final boolean channelSignal(){return channelSignal;}
    public final boolean securityToggled(){return securityToggled;}
    public final boolean legacyMotionPending(){return legacyMotionPending;}
    @Override public ModelData getModelData(){var snapshot=visual;return ModelData.builder().with(SETTINGS,snapshot.settings()).with(VISUAL,snapshot).with(NativeFluidRenderSnapshot.DATA,fluidRender).build();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);removalDrop=net.minecraft.world.item.ItemStack.EMPTY;creativeRecoveryDrop=net.minecraft.world.item.ItemStack.EMPTY;waterGushDirty=true;waterOutlets=tag.getInt("DoorWaterOutlets")&255;fluids.read(tag,registries);NativeFluidWorld.queue(this);placementRecovery=tag.getBoolean("DoorPlacementRecovery");retained=tag.copy();for(String key:java.util.List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retained.remove(key);
        settings=NativeDoorSettings.read(tag);
        if(receivingProjection)readerProjection=NativeDoorCredentialService.readProjection(tag);
        else{credentials.read(tag,registries,settings.securityMode(),settings.securityAllowed());nextCredentialTick=0;}
        ChannelList fallback=ChannelList.of(Math.max(0,tag.getInt("RedstoneChannel")));
        try{channels=tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY)?ChannelList.of(tag.getIntArray("RedstoneChannels")):fallback;}catch(IllegalArgumentException malformed){channels=fallback;}
        channelSignal=RedstoneData.Member.read(new ModernHousingTags(tag)).signal;securityToggled=tag.getBoolean("DoorSecurityToggled");if(!receivingProjection)refreshReaderProjection();legacyMotionPending=!tag.contains("SpaceDoorSchema");localDirty=true;visualDirty=true;publishLocalVisual();requestModelDataUpdate();if(level!=null&&!level.isClientSide&&controlRole())com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(this);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.merge(retained);credentials.write(tag,registries);tag.putInt("DoorWaterOutlets",waterOutlets);fluids.write(tag);writePublicSettings(tag);tag.putBoolean("DoorSecurityToggled",securityToggled);if(placementRecovery)tag.putBoolean("DoorPlacementRecovery",true);else tag.remove("DoorPlacementRecovery");}
    private void writePublicSettings(CompoundTag tag){settings.write(tag);tag.putIntArray("RedstoneChannels",channels.toArray());new RedstoneData.Member(channels.first(),channelSignal).write(new ModernHousingTags(tag));}
    /** Explicit projection: opaque retained data, reader inventories and foreign capabilities never enter a menu/update packet. */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();writePublicSettings(tag);fluids.write(tag);credentials.writeProjection(tag,level==null?0:level.getGameTime());tag.putBoolean("DoorSecurityToggled",securityToggled);if(placementRecovery)tag.putBoolean("DoorPlacementRecovery",true);return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){receivingProjection=true;try{loadWithComponents(tag,registries);}finally{receivingProjection=false;}}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){handleUpdateTag(packet.getTag(),registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    /** Configured items contain choices/channels only, matching legacy itemSettings rather than a world snapshot. */
    public final CompoundTag itemSettings(){CompoundTag tag=new CompoundTag();settings.write(tag);tag.putInt("SpaceDoorChannel",channels.first());tag.putIntArray("RedstoneChannels",channels.toArray());return tag;}
    private boolean placementRecovery;
    final void installPlacement(NativeDoorSettings next,ChannelList bank){settings=next;channels=bank;credentials.configureLocal(next.securityMode(),next.securityAllowed());refreshReaderProjection();legacyMotionPending=false;membershipDirty=true;publishLocalVisual();}
    final void retainPlacementRecovery(){placementRecovery=true;publishLocalVisual();preparePublication();setChanged();requestModelDataUpdate();}
    public final boolean placementRecovery(){return placementRecovery;}
    public static boolean validFrames(com.vandorlabs.persistence.FrameSettings f){return f!=null&&NativeDoorSettings.frame(f.front)&&NativeDoorSettings.surface(f.interior)&&NativeDoorSettings.surface(f.exterior)&&(f.artworkVersion==0||f.artworkVersion==1)&&(f.splitTop==-1||com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(f.splitTop))&&(f.splitBottom==-1||com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(f.splitBottom));}
    final boolean stageFrames(com.vandorlabs.persistence.FrameSettings frames){var old=settings;var next=new NativeDoorSettings(old.door(),old.faceTexture(),old.rawLayout(),old.tileTexture(),old.depth(),old.rawPanelMode(),old.securityMode(),old.securityAllowed(),frames,old.glass());if(old.same(next))return false;settings=next;refreshVisual(false);return true;}
    final boolean stageGlass(NativeDoorGlass glass){if(settings.glass().same(glass))return false;var old=settings;settings=new NativeDoorSettings(old.door(),old.faceTexture(),old.rawLayout(),old.tileTexture(),old.depth(),old.rawPanelMode(),old.securityMode(),old.securityAllowed(),old.frames(),glass);refreshVisual(false);return true;}
    final boolean accepts(NativeDoorEdit edit){return edit!=null&&edit.accepts(large);}
    final boolean stage(NativeDoorEdit edit,ChannelList next){
        NativeDoorSettings updated=edit.apply(settings);boolean changed=!settings.same(updated)||!channels.equals(next);
        if(!changed)return false;
        if(settings.securityMode()!=updated.securityMode()||settings.securityAllowed()!=updated.securityAllowed())securityToggled=false;
        settings=updated;membershipDirty|=!channels.equals(next);channels=next;legacyMotionPending=false;
        credentials.configureLocal(updated.securityMode(),updated.securityAllowed());refreshReaderProjection();
        refreshVisual(false);return true;
    }
    final void publish(){queueFluidParts();refreshVisual(false);preparePublication();setChanged();requestModelDataUpdate();if(level!=null&&!level.isClientSide){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);if(membershipDirty&&isChannelMemberActive())com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.channelsChanged(this);membershipDirty=false;publicationCompleted();}}
    @Override public BlockEntity channelBlockEntity(){return this;}
    protected NativeProgrammableDoorEntity controlRoot(){return level==null?null:NativeDoorLifecycle.root(level,worldPosition);}
    /** Local role only: safe during load before any neighbor query is allowed. */
    protected boolean controlRole(){return getBlockState().getBlock() instanceof NativeProgrammableDoorBlock&&getBlockState().getValue(NativeProgrammableDoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;}
    protected boolean controlStructureComplete(){return level!=null&&getBlockState().getBlock() instanceof NativeProgrammableDoorBlock block&&NativeDoorLifecycle.complete(level,worldPosition,block);}
    protected int controlLocalSignal(){return NativeDoorLifecycle.localPower(level,worldPosition);}
    protected void controlPowerChanged(){NativeDoorLifecycle.updatePower(this);}
    protected boolean readerUsable(net.minecraft.server.level.ServerPlayer player){return controlRoot()==this&&com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,this);}
    protected void preparePublication(){}
    protected void publicationCompleted(){localInputChanged();}
    @Override public boolean isChannelMemberActive(){return !placementRecovery&&(!(level instanceof net.minecraft.server.level.ServerLevel server)||com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))&&controlRole()&&com.vandorlabs.neoforge.redstone.NativeChannelMember.super.isChannelMemberActive()&&controlStructureComplete();}
    @Override public int localSignalLevel(int channel){return isChannelMemberActive()?controlLocalSignal():0;}
    @Override public void setChannelLevel(int value){boolean next=value>0;if(channelSignal==next)return;channelSignal=next;controlPowerChanged();}
    public final void localInputChanged(){localDirty=true;if(level!=null&&!level.isClientSide)tickDoor();}
    private void publishLocalVisual(){visual=new Visual(settings,getBlockState(),visual==null?com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE:visual.side(),large,placementRecovery,readerProjection);lastRawState=getBlockState();}
    public final void markVisualDirty(){visualDirty=true;}
    public final void refreshVisual(){refreshVisual(true);}
    private void refreshVisual(boolean notify){
        visualDirty=false;lastRawState=getBlockState();if(level==null||controlRoot()!=this){if(visual.settings()!=settings||visual.state()!=getBlockState()||visual.recovery()!=placementRecovery||visual.readers()!=readerProjection)publishLocalVisual();return;}
        if(!(getBlockState().getBlock() instanceof NativeProgrammableDoorBlock block)){publishLocalVisual();return;}var state=block.resolved(level,worldPosition,getBlockState());var side=NativeDoorLifecycle.panelSide(this,state);var old=visual;
        visualDirty=false;if(old.settings()==settings&&old.state()==state&&old.side()==side&&old.readers()==readerProjection)return;visual=new Visual(settings,state,side,large,placementRecovery,readerProjection);if(notify)requestModelDataUpdate();
        if(notify&&level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    public final void tickWaterGush(){if(level!=null&&!level.isClientSide&&getBlockState().getValue(NativeProgrammableDoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER&&(waterGushDirty||level.getGameTime()>=nextWaterGushTick))NativeDoorWaterGush.update(this,false);}
    public final void tickDoor(){if(getBlockState()!=lastRawState){visualDirty=true;localDirty=true;NativeFluidWorld.queue(this);}if(level==null)return;if(!controlRole()){visualDirty=false;localDirty=false;lastRawState=getBlockState();return;}if(level.isClientSide){if(visualDirty)refreshVisual();return;}if(!visualDirty&&!localDirty)return;if(!isChannelMemberActive()){visualDirty=false;localDirty=false;lastRawState=getBlockState();return;}if(visualDirty)refreshVisual();if(placementRecovery||!localDirty)return;localDirty=false;migrateSavedModel();controlPowerChanged();}
    public final NativeDoorCredentialService.Projection readerProjection(){return readerProjection;}
    private void refreshReaderProjection(){readerProjection=credentials.projection(level==null?0:level.getGameTime());}
    protected NativeProgrammableDoorEntity credentialMate(){if(level==null||!(getBlockState().getBlock() instanceof NativeProgrammableDoorBlock block))return null;var other=NativeDoorLifecycle.mate(level,worldPosition,block);return other==null?null:NativeDoorLifecycle.root(level,other);}
    private void credentialsChanged(){refreshReaderProjection();publish();var other=credentialMate();if(other!=null)other.localInputChanged();}
    private void flipCredentialToggle(){securityToggled=!securityToggled;var other=credentialMate();if(other!=null&&other.settings.securityMode()==4){other.securityToggled=securityToggled;other.credentialsChanged();}}
    public final boolean securityOpen(){if(level==null||controlRoot()!=this)return false;long now=level.getGameTime();if(credentials.readerOpen(settings.panelMode(),now))return true;var other=credentialMate();return other!=null&&other.settings.securityMode()>0&&other.credentials.readerOpen(other.settings.panelMode(),now);}
    public final boolean holdingCredential(net.minecraft.server.level.ServerPlayer player,net.minecraft.world.InteractionHand hand){return level!=null&&controlRoot()==this&&credentials.holdingCredential(player,hand);}
    public final boolean interactCredential(int side,net.minecraft.server.level.ServerPlayer player,net.minecraft.world.InteractionHand hand){return level!=null&&controlRoot()==this&&settings.securityMode()>0&&credentials.interact(side,player,hand);}
    public final net.minecraft.world.item.ItemStack takeCredentialForDrop(int side){return credentials.takeForDrop(side);}
    public final boolean opaqueCredential(int side){return credentials.opaqueOccupied(side);}
    public final CompoundTag credentialRecovery(HolderLookup.Provider registries){return credentials.rawRecovery(registries);}
    public final CompoundTag opaqueCredentialRecovery(){var tag=new CompoundTag();credentials.writeOpaqueRecovery(tag);return tag;}
    public final net.minecraft.world.item.ItemStack attachOpaqueCredentialRecovery(net.minecraft.world.item.ItemStack stack){var recovery=opaqueCredentialRecovery();if(!recovery.isEmpty()){var custom=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();custom.put(NativeDoorCredentialService.RECOVERY_KEY,recovery);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(custom));}return stack;}
    final boolean restoreOpaqueCredentialRecovery(CompoundTag recovery,HolderLookup.Provider registries){boolean restored=credentials.restoreOpaqueRecovery(recovery,registries,settings.securityMode(),settings.securityAllowed());if(restored){refreshReaderProjection();publishLocalVisual();}return restored;}

    public final void tickCredentialReaders(){if(!(level instanceof net.minecraft.server.level.ServerLevel server)||server.getGameTime()<nextCredentialTick||controlRoot()!=this)return;nextCredentialTick=Long.MAX_VALUE;credentials.scheduledTick();}
    @Override public void beforeChannelRegistration(){migrateSavedModel();credentials.resume();refreshReaderProjection();localDirty=true;tickDoor();membershipDirty=false;}
    protected Boolean legacySlidingModel(){return getBlockState().getBlock() instanceof NativeProgrammableDoorBlock block?block.slidingModel():null;}
    private void migrateSavedModel(){if(!legacyMotionPending)return;Boolean model=legacySlidingModel();if(model==null)return;var old=settings;var data=old.door();boolean sliding=model;var migrated=new com.vandorlabs.persistence.SpaceDoorData(data.design,data.detail,data.framed,data.direction,sliding||data.middle,sliding,data.hinges,data.trigger,data.panel);settings=new NativeDoorSettings(migrated,old.faceTexture(),old.rawLayout(),old.tileTexture(),sliding?0:old.depth(),old.rawPanelMode(),old.securityMode(),old.securityAllowed(),old.frames(),old.glass());legacyMotionPending=false;publishLocalVisual();visualDirty=true;if(level!=null&&!level.isClientSide){preparePublication();setChanged();}}
    @Override public void onLoad(){super.onLoad();NativeDoorTargets.add(this);waterGushDirty=true;NativeFluidWorld.queue(this);localDirty=true;visualDirty=true;if(level!=null){if(level.isClientSide)migrateSavedModel();else if(controlRole())com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(this);}}
    @Override public void setRemoved(){NativeDoorTargets.remove(this);NativeFluidWorld.removed(this);com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeDoorTargets.remove(this);NativeFluidWorld.unloaded(this);com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    /** Per-part slots are intentionally independent, including the upper part of a linked door. */
    @Override public BlockEntity fluidOwner(){return this;}
    @Override public NativeFluidSlots containedFluids(){return fluids;}
    private final NativeOpeningFluidRegions fluidSurfaceRegions=new NativeOpeningFluidRegions();
    @Override public NativeFluidRegions fluidSurfaceRegions(){return fluidSurfaceRegions.snapshot();}
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
    @Override public void containedFluidsChanged(){if(level!=null&&!level.isClientSide){var root=NativeDoorLifecycle.root(level,worldPosition);if(root!=null)root.waterGushDirty=true;}setChanged();requestModelDataUpdate();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    /** Rebuild only on the owning thread, then publish one complete world-free geometry record. */
    @Override public void refreshFluidGeometry(){
        var old=fluidGeometry;var root=level==null?null:NativeDoorLifecycle.root(level,worldPosition);
        if(root==null||root.large()){if(old.key()!=null)fluidGeometry=new FluidGeometry(null,null,NO_FLUID_VOLUME,old.revision()+1);publishEffectiveFluids(0);fluidSurfaceRegions.clear();return;}
        var state=NativeDoorLifecycle.resolved(level,worldPosition,getBlockState());var profile=root.settings();var door=profile.door();
        var key=new FluidKey(door.sliding,door.framed,door.hinges,profile.depth(),state.getValue(NativeProgrammableDoorBlock.FACING),state.getValue(NativeProgrammableDoorBlock.OPEN),state.getValue(NativeProgrammableDoorBlock.HINGE)==net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT,state.getValue(NativeProgrammableDoorBlock.PAIRED));
        int water=NativeDoorOpeningFluids.sources(level,worldPosition,key.face(),root.getBlockState().getValue(NativeProgrammableDoorBlock.OPEN),fluids.snapshot());publishEffectiveFluids(water);
        if(key.equals(old.key())){prepareFluidSurfaces(root.getBlockState().getValue(NativeProgrammableDoorBlock.OPEN));return;}
        var built=NativeDoorFluidGeometry.build(profile,key.face(),key.open(),key.left(),key.paired(),worldPosition.getX(),worldPosition.getY(),worldPosition.getZ());
        fluidGeometry=new FluidGeometry(key,built.plane(),built.volume(),old.revision()+1);
        prepareFluidSurfaces(root.getBlockState().getValue(NativeProgrammableDoorBlock.OPEN));
    }
    private void prepareFluidSurfaces(boolean open){var plane=fluidGeometry.plane();fluidSurfaceRegions.prepareDoor(this,plane.x(),plane.z(),plane.near(),plane.far(),open,effectiveFluids);}
    private void publishEffectiveFluids(int water){var stored=fluids.snapshot();if(effectiveBase!=null&&stored.revision==effectiveBase.revision&&water==effectiveWater)return;effectiveBase=stored;effectiveWater=water;effectiveFluids=stored.withOpeningWater(water,++effectiveRevision);}
    protected void queueFluidParts(){NativeFluidWorld.queue(this);if(level!=null){BlockPos lower=NativeDoorLifecycle.lower(getBlockState(),worldPosition);NativeFluidWorld.queueAt(level,lower);NativeFluidWorld.queueAt(level,lower.above());}}

}
