package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.NativePlatformComponents;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Inactive standalone owner foundation. Loading does not discover neighbors or prepare dense geometry. */
public final class NativeCanopyEntity extends BlockEntity implements com.vandorlabs.neoforge.redstone.NativeChannelMember {
    public static final String RECOVERY="CanopyRecoverySnapshot", UNINITIALIZED="CanopyUninitialized", PLACEMENT_ITEM="CanopyPlacementItem";
    public record Publication(NativeCanopyRenderSnapshot visual,NativeCanopyGeometry.Snapshot geometry,
            float progress,boolean targetOpen,boolean waiting,boolean draws,boolean resolved,boolean inert){}
    final NativeCanopyControl.State control=new NativeCanopyControl.State();
    boolean channelPowered;
    private final String kind;
    private NativeCanopySettings settings=NativeCanopySettings.fresh();
    private NativeCanopyLinks links;
    private CompoundTag retained=new CompoundTag();
    private volatile Publication publication;
    private boolean inert=true,initialized,unloading,removing,pending=true;
    public NativeCanopyEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,String kind){
        super(type,pos,state);this.kind=kind;NativeCanopyModels.builtin().dimensions(kind);links=NativeCanopyLinks.read(new CompoundTag(),pos);publishUnresolved();
    }
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
        int width=localWidth();NativeCanopyRenderSnapshot visual=new NativeCanopyRenderSnapshot(kind,facing().get3DDataValue(),Math.min(9,width),0,0,links.centered(),settings);
        publication=new Publication(visual,null,links.progress(),links.open(),links.waiting(),localDraws(),false,inert);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        control.reset();channelPowered=false;var original=tag.copy();initialized=!tag.getBoolean(UNINITIALIZED);try{
            NativePlatformComponents.requireSupported(original,registries);
            if(!initialized||tag.contains("NeoForgeData")&&!tag.contains("NeoForgeData",Tag.TAG_COMPOUND)||tag.contains(RECOVERY)||tag.contains("NativeCanopySchema")&&(!tag.contains("NativeCanopySchema",Tag.TAG_INT)||tag.getInt("NativeCanopySchema")!=1))throw new IllegalArgumentException("Unsupported canopy owner");
            var nextSettings=NativeCanopySettings.read(tag);var nextLinks=NativeCanopyLinks.read(tag,worldPosition);
            if(NativeCanopyModels.builtin().dimensions(kind).width()*(nextLinks.pair()==null?1:nextLinks.joinedWidth())>9)throw new IllegalArgumentException("Unsupported canopy group width");
            try(var platform=NativePlatformComponents.guardLoad(this)){super.loadAdditional(tag.copy(),registries);platform.requireSuccessful();}
            settings=nextSettings;links=nextLinks;retained=original;NativePlatformComponents.forgetLoadedFields(retained);inert=level!=null&&level.isClientSide&&tag.getBoolean("NativeCanopyInert");
        }catch(RuntimeException failure){retained=original;settings=NativeCanopySettings.fresh();links=NativeCanopyLinks.read(new CompoundTag(),worldPosition);inert=true;}
        pending=true;publishUnresolved();
    }
    private void writeOwn(CompoundTag tag){
        // Null optional live fields must not inherit stale values from an earlier valid retained save.
        for(String key:new String[]{"Anchor","AnchorOffset","Pair","PairOffset","CanopyFootprint"})tag.remove(key);
        settings.write(tag);links.write(tag,worldPosition,publication.visual().mask(),publication.visual().roof(),width());tag.putInt("NativeCanopySchema",1);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        tag.merge(retained.copy());if(inert){if(!initialized)tag.putBoolean(UNINITIALIZED,true);NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);writeOwn(tag);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();writeOwn(tag);tag.putBoolean("NativeCanopyInert",inert);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    /** Local-only lifecycle hooks; installed-thread preparation is deliberately separate. */
    @Override public void onLoad(){super.onLoad();unloading=false;pending=true;if(level!=null&&!level.isClientSide)com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(this);}
    @Override public void setRemoved(){unloading=true;pending=false;com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){unloading=true;pending=false;com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    @Override public void setBlockState(BlockState state){super.setBlockState(state);if(links!=null){pending=true;publishUnresolved();}}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public com.vandorlabs.redstone.ChannelList getRedstoneChannels(){return available()&&publication.draws()&&NativeCanopyControl.opening(this)&&settings.data().redstone?settings.data().channels:com.vandorlabs.redstone.ChannelList.EMPTY;}
    @Override public boolean isChannelMemberActive(){return available()&&NativeCanopyLifecycle.installed(this);}
    @Override public int localSignalLevel(int channel){return NativeCanopyControl.localPower(this);}
    @Override public void setChannelLevel(int value){channelPowered=value>0;NativeCanopyControl.refreshPower(this);}
    @Override public void beforeChannelRegistration(){NativeCanopyControl.refreshPower(this);}
    void markRemoving(){removing(true);}
    void removing(boolean value){removing=value;publishUnresolved();}
    void installItem(NativeCanopyPlacement.Prepared prepared){unloading=false;removing=false;clearRemoved();setBlockState(prepared.state());loadWithComponents(prepared.initialSnapshot(),level.registryAccess());if(inert)throw new IllegalArgumentException("Failed canopy platform installation");placementChoices(prepared.settings());}
    void retainPlacementRecovery(NativeCanopyPlacement.Prepared prepared,CompoundTag item){initialized=true;unloading=false;removing=false;clearRemoved();retained=prepared.snapshot();retained.put(RECOVERY,prepared.snapshot());retained.put(PLACEMENT_ITEM,item.copy());settings=NativeCanopySettings.fresh();links=NativeCanopyLinks.read(new CompoundTag(),worldPosition);inert=true;pending=true;publishUnresolved();}
    void placementChoices(NativeCanopySettings next){
        settings=next;boolean centered=com.vandorlabs.persistence.CanopySettingsData.supports(kind)||kind.startsWith("space_visor_");boolean hollow=com.vandorlabs.persistence.CanopySettingsData.supports(kind);
        var fresh=new NativeCanopyLinks(links.anchor(),links.pair(),links.joinedWidth(),centered,hollow,links.footprint(),links.released(),links.waiting(),links.open(),links.progress(),links.powerKnown(),links.lastPower(),links.lastPolarity());
        var cells=NativeCanopyFootprint.cells(kind,worldPosition,facing(),settings,fresh);links=new NativeCanopyLinks(cells.contains(worldPosition)?worldPosition:fresh.anchor(),fresh.pair(),fresh.joinedWidth(),centered,hollow,cells,java.util.Set.of(),fresh.waiting(),fresh.open(),fresh.progress(),fresh.powerKnown(),fresh.lastPower(),fresh.lastPolarity());pending=true;publishUnresolved();
    }
    void helperOf(NativeCanopyEntity parent){initialized=true;inert=false;settings=parent.settings;links=new NativeCanopyLinks(parent.getBlockPos(),null,1,parent.links.centered(),parent.links.hollow(),null,java.util.Set.of(),false,false,0,false,false,false);pending=true;publishUnresolved();}
    void stageSettings(NativeCanopySettings next){settings=next;pending=true;publishUnresolved();}
    void configurationRole(NativeCanopyEntity owner){initialized=true;inert=false;links=new NativeCanopyLinks(owner.getBlockPos(),links.pair(),links.joinedWidth(),owner.links().centered(),links.hollow(),links.footprint(),links.released(),links.waiting(),links.open(),links.progress(),links.powerKnown(),links.lastPower(),links.lastPolarity());pending=true;publishUnresolved();}
    void configurationFootprint(java.util.List<BlockPos> cells){links=new NativeCanopyLinks(links.anchor(),links.pair(),links.joinedWidth(),links.centered(),links.hollow(),java.util.List.copyOf(cells),java.util.Set.of(),links.waiting(),links.open(),links.progress(),links.powerKnown(),links.lastPower(),links.lastPolarity());pending=true;publishUnresolved();}
    void configurationSettings(NativeCanopySettings next){settings=next;links=new NativeCanopyLinks(links.anchor(),links.pair(),links.joinedWidth(),links.centered(),true,links.footprint(),links.released(),links.waiting(),links.open(),links.progress(),links.powerKnown(),links.lastPower(),links.lastPolarity());pending=true;publishUnresolved();}
    void retainConfigurationRecovery(CompoundTag snapshot){retained=snapshot.copy();retained.put(RECOVERY,snapshot.copy());inert=true;pending=true;publishUnresolved();setChanged();}
    /** Full retained item is the sole recoverable owner after a consumed placement failure. */
    public net.minecraft.world.item.ItemStack placementRecoveryItem(){if(!retained.contains(PLACEMENT_ITEM,Tag.TAG_COMPOUND)||level==null)return net.minecraft.world.item.ItemStack.EMPTY;try{return com.vandorlabs.neoforge.inventory.NativeInventoryItems.modern(retained.getCompound(PLACEMENT_ITEM),level.registryAccess());}catch(RuntimeException failure){return net.minecraft.world.item.ItemStack.EMPTY;}}
    public boolean initialized(){return initialized;}
    void stageLinks(NativeCanopyLinks next){links=next;pending=true;publishUnresolved();}
    void notifyChanged(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    boolean pending(){return pending;}
    void prepared(Publication next){publication=next;pending=false;}
}
