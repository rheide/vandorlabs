package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.NativePlatformComponents;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Standalone owner. Loading does not discover neighbors or prepare dense geometry. */
public final class NativeCanopyEntity extends BlockEntity implements com.vandorlabs.neoforge.redstone.NativeChannelMember {
    public static final net.neoforged.neoforge.client.model.data.ModelProperty<NativeCanopyRenderSnapshot> TERRAIN=new net.neoforged.neoforge.client.model.data.ModelProperty<>();
    private volatile net.neoforged.neoforge.client.model.data.ModelData modelData=net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
    public static final String MOTION_REVISION="NativeCanopyMotionRevision";
    public static final String RECOVERY="CanopyRecoverySnapshot", UNINITIALIZED="CanopyUninitialized", PLACEMENT_ITEM="CanopyPlacementItem";
    public record Publication(NativeCanopyRenderSnapshot visual,NativeCanopyGeometry.Snapshot geometry,
            float progress,boolean targetOpen,boolean waiting,boolean draws,boolean resolved,boolean inert){}
    final NativeCanopyControl.State control=new NativeCanopyControl.State();
    boolean channelPowered,suppressHarvest;
    private final String kind;
    private NativeCanopySettings settings=NativeCanopySettings.fresh();
    private NativeCanopyLinks links;
    private CompoundTag retained=new CompoundTag();
    private volatile Publication publication;
    private volatile long motionRevision;
    private boolean revisionKnown;
    private boolean inert=true,initialized,unloading,removing,pending=true,announceLoad=true,topologyPending=true;
    private int contacts,roofContacts;
    public NativeCanopyEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,String kind){
        super(type,pos,state);this.kind=kind;NativeCanopyModels.builtin().dimensions(kind);links=NativeCanopyLinks.read(new CompoundTag(),pos);publishUnresolved();
    }
    @Override public net.neoforged.neoforge.client.model.data.ModelData getModelData(){return modelData;}
    public String kind(){return kind;}
    public NativeCanopySettings settings(){return settings;}
    public NativeCanopyLinks links(){return links;}
    public Publication publication(){return publication;}
    public boolean inert(){return inert;}
    public boolean available(){return !inert&&!unloading&&!removing&&!isRemoved();}
    public CompoundTag recoverySnapshot(){return retained.copy();}
    public Direction facing(){return getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)?getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING):Direction.NORTH;}
    private int localWidth(){return NativeCanopyModels.builtin().dimensions(kind).width()*(links.pair()==null?1:links.joinedWidth());}
    /** Owning-thread Source ownership getter; worker/render consumers use the immutable publication. */
    public int width(){if(level!=null){var owner=NativeCanopyLifecycle.owner(this);if(owner!=this)return owner.localWidth();}return localWidth();}
    private boolean localDraws(){return !removing&&(links.anchor()==null||links.anchor().equals(worldPosition))&&(links.pair()==null||links.pair().equals(worldPosition));}
    private void publishUnresolved(){
        int width=localWidth();NativeCanopyRenderSnapshot visual=new NativeCanopyRenderSnapshot(kind,facing().get3DDataValue(),Math.min(9,width),contacts,roofContacts,links.centered(),settings);
        publication=new Publication(visual,null,links.progress(),links.open(),links.waiting(),localDraws(),false,inert);modelData=kind.equals("regular_canopy_glass")&&publication.draws()?net.neoforged.neoforge.client.model.data.ModelData.builder().with(TERRAIN,visual).build():net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        control.reset();channelPowered=false;suppressHarvest=false;var original=tag.copy();initialized=!tag.getBoolean(UNINITIALIZED);try{
            NativePlatformComponents.requireSupported(original,registries);
            if(tag.contains(MOTION_REVISION)&&!tag.contains(MOTION_REVISION,Tag.TAG_LONG))throw new IllegalArgumentException("Unsupported canopy motion revision");
            if(!initialized||tag.contains("NeoForgeData")&&!tag.contains("NeoForgeData",Tag.TAG_COMPOUND)||tag.contains(RECOVERY)||tag.contains("NativeCanopySchema")&&(!tag.contains("NativeCanopySchema",Tag.TAG_INT)||tag.getInt("NativeCanopySchema")!=1))throw new IllegalArgumentException("Unsupported canopy owner");
            var nextSettings=NativeCanopySettings.read(tag);var nextLinks=NativeCanopyLinks.read(tag,worldPosition);
            if(NativeCanopyModels.builtin().dimensions(kind).width()*(nextLinks.pair()==null?1:nextLinks.joinedWidth())>9)throw new IllegalArgumentException("Unsupported canopy group width");
            try(var platform=NativePlatformComponents.guardLoad(this)){super.loadAdditional(tag.copy(),registries);platform.requireSuccessful();}
            motionRevision=tag.getLong(MOTION_REVISION);revisionKnown=tag.contains(MOTION_REVISION,Tag.TAG_LONG);settings=nextSettings;links=nextLinks;retained=original;NativePlatformComponents.forgetLoadedFields(retained);inert=level!=null&&level.isClientSide&&tag.getBoolean("NativeCanopyInert");
        }catch(RuntimeException failure){retained=original;settings=NativeCanopySettings.fresh();links=NativeCanopyLinks.read(new CompoundTag(),worldPosition);inert=true;}
        contacts=tag.contains("CanopyMask",Tag.TAG_INT)?tag.getInt("CanopyMask")&15:0;roofContacts=tag.contains("CanopyRoofMask",Tag.TAG_INT)?tag.getInt("CanopyRoofMask")&contacts:contacts;topologyPending=true;pending=true;publishUnresolved();
    }
    private void writeOwn(CompoundTag tag){
        // Null optional live fields must not inherit stale values from an earlier valid retained save.
        for(String key:new String[]{"Anchor","AnchorOffset","Pair","PairOffset","CanopyFootprint"})tag.remove(key);
        settings.write(tag);links.write(tag,worldPosition,publication.visual().mask(),publication.visual().roof(),width());tag.putInt("NativeCanopySchema",1);tag.putLong(MOTION_REVISION,motionRevision);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        tag.merge(retained.copy());if(inert){if(!initialized)tag.putBoolean(UNINITIALIZED,true);NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);writeOwn(tag);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();writeOwn(tag);tag.putBoolean("NativeCanopyInert",inert);tag.putBoolean(UNINITIALIZED,!initialized);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    /** Serial arithmetic accepts wraparound while bounding the unambiguous delivery window to half the sequence space. */
    private boolean staleUpdate(CompoundTag tag){if(!revisionKnown)return false;if(!tag.contains(MOTION_REVISION))return true;return tag.contains(MOTION_REVISION,Tag.TAG_LONG)&&tag.getLong(MOTION_REVISION)-motionRevision<=0;}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){var tag=packet.getTag();if(!tag.isEmpty()&&!staleUpdate(tag))loadWithComponents(tag,registries);}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){if(!staleUpdate(tag))loadWithComponents(tag,registries);}
    /** Local-only lifecycle hooks; installed-thread preparation is deliberately separate. */
    @Override public void onLoad(){super.onLoad();NativeCanopyTargets.add(this);NativeCanopyTopology.queue(level,worldPosition);announceLoad=true;unloading=false;pending=true;if(level!=null&&!level.isClientSide)com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(this);}
    @Override public void setRemoved(){NativeCanopyTopology.queue(level,worldPosition);NativeCanopyNotifications.changed(this);NativeCanopyLifecycle.clientRemoving(this);NativeCanopyTargets.remove(this);NativeCanopySweeps.retire(this);NativeCanopyCloseService.retire(this);unloading=true;pending=false;com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeCanopyTopology.queue(level,worldPosition);NativeCanopyTargets.remove(this);NativeCanopySweeps.retire(this);NativeCanopyCloseService.retire(this);NativeCanopyNotifications.changed(this);unloading=true;pending=false;com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    @Override public void setBlockState(BlockState state){super.setBlockState(state);if(links!=null){topologyPending=true;NativeCanopyTopology.queue(level,worldPosition);pending=true;publishUnresolved();}}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public com.vandorlabs.redstone.ChannelList getRedstoneChannels(){return available()&&publication.draws()&&NativeCanopyControl.opening(this)&&settings.data().redstone?settings.data().channels:com.vandorlabs.redstone.ChannelList.EMPTY;}
    @Override public boolean isChannelMemberActive(){return available()&&NativeCanopyLifecycle.installed(this);}
    @Override public int localSignalLevel(int channel){return NativeCanopyControl.localPower(this);}
    @Override public void setChannelLevel(int value){channelPowered=value>0;NativeCanopyControl.refreshPower(this);}
    @Override public void beforeChannelRegistration(){NativeCanopyControl.refreshPower(this);}
    void markRemoving(){removing(true);}
    boolean removing(){return removing;}
    void uninitializedRemnant(){var original=saveWithoutMetadata(level.registryAccess());initialized=false;inert=true;retained=original;retained.putBoolean(UNINITIALIZED,true);links=NativeCanopyLinks.read(new CompoundTag(),worldPosition);publishUnresolved();setChanged();}
    void removing(boolean value){removing=value;publishUnresolved();}
    void installItem(NativeCanopyPlacement.Prepared prepared){unloading=false;removing=false;clearRemoved();setBlockState(prepared.state());loadWithComponents(prepared.initialSnapshot(),level.registryAccess());if(inert)throw new IllegalArgumentException("Failed canopy platform installation");placementChoices(prepared.settings());}
    void retainPlacementRecovery(NativeCanopyPlacement.Prepared prepared,CompoundTag item){initialized=true;unloading=false;removing=false;clearRemoved();retained=prepared.snapshot();retained.put(RECOVERY,prepared.snapshot());retained.put(PLACEMENT_ITEM,item.copy());settings=NativeCanopySettings.fresh();links=NativeCanopyLinks.read(new CompoundTag(),worldPosition);inert=true;pending=true;publishUnresolved();}
    void placementChoices(NativeCanopySettings next){
        settings=next;boolean centered=com.vandorlabs.persistence.CanopySettingsData.supports(kind)||kind.startsWith("space_visor_");boolean hollow=com.vandorlabs.persistence.CanopySettingsData.supports(kind);
        var fresh=new NativeCanopyLinks(links.anchor(),links.pair(),links.joinedWidth(),centered,hollow,links.footprint(),links.released(),links.waiting(),links.open(),links.progress(),links.powerKnown(),links.lastPower(),links.lastPolarity());
        var cells=NativeCanopyFootprint.cells(kind,worldPosition,facing(),settings,fresh);links=new NativeCanopyLinks(cells.contains(worldPosition)?worldPosition:fresh.anchor(),fresh.pair(),fresh.joinedWidth(),centered,hollow,cells,java.util.Set.of(),fresh.waiting(),fresh.open(),fresh.progress(),fresh.powerKnown(),fresh.lastPower(),fresh.lastPolarity());pending=true;publishUnresolved();
    }
    void helperOf(NativeCanopyEntity parent){topologyPending=true;initialized=true;inert=false;settings=parent.settings;links=new NativeCanopyLinks(parent.getBlockPos(),null,1,parent.links.centered(),parent.links.hollow(),null,java.util.Set.of(),false,false,0,false,false,false);pending=true;publishUnresolved();}
    void stageSettings(NativeCanopySettings next){topologyPending=true;settings=next;pending=true;publishUnresolved();}
    void configurationRole(NativeCanopyEntity owner){initialized=true;inert=false;links=new NativeCanopyLinks(owner.getBlockPos(),links.pair(),links.joinedWidth(),owner.links().centered(),links.hollow(),links.footprint(),links.released(),links.waiting(),links.open(),links.progress(),links.powerKnown(),links.lastPower(),links.lastPolarity());pending=true;publishUnresolved();}
    void configurationFootprint(java.util.List<BlockPos> cells){links=new NativeCanopyLinks(links.anchor(),links.pair(),links.joinedWidth(),links.centered(),links.hollow(),java.util.List.copyOf(cells),java.util.Set.of(),links.waiting(),links.open(),links.progress(),links.powerKnown(),links.lastPower(),links.lastPolarity());pending=true;publishUnresolved();}
    void configurationSettings(NativeCanopySettings next){topologyPending=true;settings=next;links=new NativeCanopyLinks(links.anchor(),links.pair(),links.joinedWidth(),links.centered(),true,links.footprint(),links.released(),links.waiting(),links.open(),links.progress(),links.powerKnown(),links.lastPower(),links.lastPolarity());pending=true;publishUnresolved();}
    void retainConfigurationRecovery(CompoundTag snapshot){retained=snapshot.copy();if(!retained.contains(RECOVERY))retained.put(RECOVERY,snapshot.copy());initialized=true;links=NativeCanopyLinks.read(new CompoundTag(),worldPosition);inert=true;pending=true;publishUnresolved();setChanged();}
    /** Full retained item is the sole recoverable owner after a consumed placement failure. */
    public net.minecraft.world.item.ItemStack placementRecoveryItem(){if(!retained.contains(PLACEMENT_ITEM,Tag.TAG_COMPOUND)||level==null)return net.minecraft.world.item.ItemStack.EMPTY;try{return com.vandorlabs.neoforge.inventory.NativeInventoryItems.modern(retained.getCompound(PLACEMENT_ITEM),level.registryAccess());}catch(RuntimeException failure){return net.minecraft.world.item.ItemStack.EMPTY;}}
    public boolean initialized(){return initialized;}
    void stageLinks(NativeCanopyLinks next){if(!java.util.Objects.equals(links.anchor(),next.anchor())||!java.util.Objects.equals(links.pair(),next.pair())||links.joinedWidth()!=next.joinedWidth()||!links.released().equals(next.released()))topologyPending=true;links=next;pending=true;publishUnresolved();}
    void notifyChanged(){NativeCanopyTopology.queue(level,worldPosition);if(level!=null&&!level.isClientSide){motionRevision++;revisionKnown=true;}setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);NativeCanopyNotifications.changed(this);}
    void topologyDirty(){topologyPending=true;pending=true;}
    boolean topologyPending(){return topologyPending;}
    void contacts(NativeCanopyTopology.Masks next){topologyPending=false;if(contacts!=next.mask()||roofContacts!=next.roof()){contacts=next.mask();roofContacts=next.roof();publishUnresolved();}}
    boolean pending(){return pending;}
    void prepared(Publication next){publication=next;pending=false;if(announceLoad){announceLoad=false;NativeCanopyNotifications.changed(this);}if(kind.equals("regular_canopy_glass")&&level!=null&&level.isClientSide){requestModelDataUpdate();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}}
}
