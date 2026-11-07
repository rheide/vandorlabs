package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.NativePlatformComponents;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Inactive standalone owner foundation. Loading does not discover neighbors or prepare dense geometry. */
public final class NativeCanopyEntity extends BlockEntity {
    public static final String RECOVERY="CanopyRecoverySnapshot";
    public record Publication(NativeCanopyRenderSnapshot visual,NativeCanopyGeometry.Snapshot geometry,
            float progress,boolean targetOpen,boolean waiting,boolean draws,boolean resolved,boolean inert){}
    private final String kind;
    private NativeCanopySettings settings=NativeCanopySettings.fresh();
    private NativeCanopyLinks links;
    private CompoundTag retained=new CompoundTag();
    private volatile Publication publication;
    private boolean inert,unloading,removing,pending=true;
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
        var original=tag.copy();try{
            NativePlatformComponents.requireSupported(original,registries);
            if(tag.contains("NeoForgeData")&&!tag.contains("NeoForgeData",Tag.TAG_COMPOUND)||tag.contains(RECOVERY)||tag.contains("NativeCanopySchema")&&(!tag.contains("NativeCanopySchema",Tag.TAG_INT)||tag.getInt("NativeCanopySchema")!=1))throw new IllegalArgumentException("Unsupported canopy owner");
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
        tag.merge(retained.copy());if(inert){NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);writeOwn(tag);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();writeOwn(tag);tag.putBoolean("NativeCanopyInert",inert);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    /** Local-only lifecycle hooks; installed-thread preparation is deliberately separate. */
    @Override public void onLoad(){super.onLoad();unloading=false;pending=true;}
    @Override public void setRemoved(){unloading=true;pending=false;super.setRemoved();}
    @Override public void onChunkUnloaded(){unloading=true;pending=false;super.onChunkUnloaded();}
    @Override public void setBlockState(BlockState state){super.setBlockState(state);if(links!=null){pending=true;publishUnresolved();}}
    void markRemoving(){removing(true);}
    void removing(boolean value){removing=value;publishUnresolved();}
    void placementChoices(NativeCanopySettings next){
        settings=next;boolean centered=com.vandorlabs.persistence.CanopySettingsData.supports(kind)||kind.startsWith("space_visor_");boolean hollow=com.vandorlabs.persistence.CanopySettingsData.supports(kind);
        var fresh=new NativeCanopyLinks(links.anchor(),links.pair(),links.joinedWidth(),centered,hollow,links.footprint(),links.released(),links.waiting(),links.open(),links.progress(),links.powerKnown(),links.lastPower(),links.lastPolarity());
        var cells=NativeCanopyFootprint.cells(kind,worldPosition,facing(),settings,fresh);links=new NativeCanopyLinks(cells.contains(worldPosition)?worldPosition:fresh.anchor(),fresh.pair(),fresh.joinedWidth(),centered,hollow,cells,java.util.Set.of(),fresh.waiting(),fresh.open(),fresh.progress(),fresh.powerKnown(),fresh.lastPower(),fresh.lastPolarity());pending=true;publishUnresolved();
    }
    void helperOf(NativeCanopyEntity parent){settings=parent.settings;links=new NativeCanopyLinks(parent.getBlockPos(),null,1,parent.links.centered(),parent.links.hollow(),null,java.util.Set.of(),false,false,0,false,false,false);pending=true;publishUnresolved();}
    void stageLinks(NativeCanopyLinks next){links=next;pending=true;publishUnresolved();}
    void notifyChanged(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    boolean pending(){return pending;}
    void prepared(Publication next){publication=next;pending=false;}
}
