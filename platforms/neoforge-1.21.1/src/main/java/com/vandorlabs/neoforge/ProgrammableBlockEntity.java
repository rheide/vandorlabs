package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public class ProgrammableBlockEntity extends BlockEntity implements com.vandorlabs.surface.TextureJoinOwner {
    public static final ModelProperty<ProgrammableBlockDialog.State> SETTINGS=new ModelProperty<>();
    public static final ModelProperty<JoinedCubeFaces> JOINED=new ModelProperty<>();
    private volatile JoinedCubeFaces joinedFaces=JoinedCubeFaces.NONE;
    private volatile ProgrammableBlockDialog.State settings=defaults();
    private int sideTexture=-1,rawLayout=1;
    private boolean tileSides=true;
    private com.vandorlabs.surface.TextureJoinSettings textureJoin=com.vandorlabs.surface.TextureJoinSettings.DEFAULT;
    public com.vandorlabs.surface.TextureJoinSettings textureJoin(){return textureJoin;}
    public void setTextureJoin(com.vandorlabs.surface.TextureJoinSettings next){if(textureJoin().equals(next))return;textureJoin=java.util.Objects.requireNonNull(next);if(this instanceof com.vandorlabs.neoforge.shape.NativeWallEntity wall){com.vandorlabs.neoforge.shape.NativeWallLayouts.invalidate(wall);com.vandorlabs.neoforge.fluid.NativeFluidWorld.queue(wall);}refresh();}
    private CompoundTag retainedSettings=new CompoundTag();
    public ProgrammableBlockEntity(BlockPos pos,BlockState state){this(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,defaults());}
    protected ProgrammableBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state,ProgrammableBlockDialog.State initial){
        super(type,pos,state);settings=initial;rawLayout=initial.layout;tileSides=initial.layout==1;
    }
    protected ProgrammableBlockDialog.State modelSettings(){return settings;}
    public static ProgrammableBlockDialog.State defaults(){return new ProgrammableBlockDialog.State(0,1,false,new int[]{-1,-1,-1,-1,-1,-1},-1);}
    public ProgrammableBlockDialog.State settings(){return settings;}
    protected final int sideTexture(){return sideTexture;}
    /** Family adapters validate the selected finish before changing this shared saved field. */
    protected final void setSideTexture(int value){sideTexture=value;}
    public JoinedCubeFaces joinedFaces(){return joinedFaces;}
    public boolean publishJoined(JoinedCubeFaces next){
        if(joinedFaces.equals(next))return false;
        joinedFaces=next;requestModelDataUpdate();
        if(level!=null&&level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
        return true;
    }
    protected final void refresh(){
        joinedFaces=JoinedCubeFaces.NONE;setChanged();requestModelDataUpdate();
        if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    public void apply(ProgrammableBlockDialog.State value){if(updateSettings(value))refresh();}
    /** Install housing locally so family adapters can publish an atomic projection before notifying. */
    protected final boolean updateSettings(ProgrammableBlockDialog.State value){
        int[] faces=value.faces();for(int i=0;i<6;i++)faces[i]=faces[i]<0?-1:ModernFinishCatalog.storedChoice(faces[i]);
        int main=ModernFinishCatalog.storedChoice(value.mainTexture);boolean changed=settings.mainTexture!=main||settings.layout!=value.layout||settings.facesEnabled!=value.facesEnabled;
        for(int i=0;i<6;i++)if(settings.face(i)!=faces[i])changed=true;
        if(!changed)return false;
        if(settings.layout!=value.layout){rawLayout=value.layout;tileSides=value.layout==1;}
        settings=new ProgrammableBlockDialog.State(main,value.layout,value.facesEnabled,faces,-1);return true;
    }
    /** Bounded live choices; never serialize platform attachments, inventories or unrelated retained fields. */
    protected final CompoundTag copiedHousingFields(){
        var fields=new CompoundTag();HousingSettings.write(new ModernHousingTags(fields),settings.mainTexture,sideTexture,rawLayout,tileSides,settings.facesEnabled,settings.faces());
        var housing=HousingSettings.read(new ModernHousingTags(fields),settings.mainTexture,tileSides,ModernFinishCatalog::storedChoice,ModernFinishCatalog::validChoice);
        var snapshot=NativeCopiedHousingMetadata.snapshot(housing,retainedSettings);textureJoin().write(new ModernHousingTags(snapshot));return snapshot;
    }
    /** Validate and install dormant metadata locally, allowing a family to publish one coherent projection. */
    protected final boolean installCopiedMetadata(com.vandorlabs.persistence.FrameSettings frames,com.vandorlabs.redstone.ChannelList channels,int range,boolean local,com.vandorlabs.redstone.ChannelList transmitted){
        if(!(level instanceof net.minecraft.server.level.ServerLevel server)||!server.getServer().isSameThread()||!level.hasChunkAt(worldPosition)||level.getBlockEntity(worldPosition)!=this||isRemoved()||!NativeCopiedHousingMetadata.valid(frames,channels,range,transmitted))return false;
        try{retainedSettings=NativeCopiedHousingMetadata.edit(retainedSettings,frames,channels,range,local,transmitted);}catch(IllegalArgumentException unsupported){return false;}
        return true;
    }
    /** Preserve dormant Source configuration without reloading physical/platform data or tile identity. */
    public final boolean configureCopiedMetadata(com.vandorlabs.persistence.FrameSettings frames,com.vandorlabs.redstone.ChannelList channels,int range,boolean local,com.vandorlabs.redstone.ChannelList transmitted){
        if(!installCopiedMetadata(frames,channels,range,local,transmitted))return false;setChanged();return true;
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        retainedSettings=tag.copy();for(String key:java.util.List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retainedSettings.remove(key);
        HousingSettings saved=HousingSettings.read(new ModernHousingTags(tag),settings.mainTexture,tileSides,
            ModernFinishCatalog::storedChoice,ModernFinishCatalog::validChoice);
        textureJoin=com.vandorlabs.surface.TextureJoinSettings.read(new ModernHousingTags(tag));
        rawLayout=saved.rawLayout;tileSides=saved.tileSides;sideTexture=saved.sideTexture;
        settings=new ProgrammableBlockDialog.State(saved.mainTexture,saved.layout(),saved.facesEnabled,saved.faces(),-1);
        joinedFaces=JoinedCubeFaces.NONE;housingLoaded();requestModelDataUpdate();
        if(level!=null&&level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    /** Called after local housing installation and before model/update notification. */
    protected void housingLoaded(){ }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.merge(retainedSettings);textureJoin.write(new ModernHousingTags(tag));
        HousingSettings.write(new ModernHousingTags(tag),settings.mainTexture,sideTexture,rawLayout,tileSides,settings.facesEnabled,settings.faces());
    }
    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components){
        super.collectImplicitComponents(components);
        if(level!=null){CompoundTag saved=saveWithId(level.registryAccess());
            for(String key:java.util.List.of("x","y","z","ChannelLevel","TriggerLocalLevel","ChannelSignal","channelSignal","JoinedSignalLevel","LightGroupPowered"))saved.remove(key);
            components.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(saved));}
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveWithoutMetadata(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public ModelData getModelData(){return ModelData.builder().with(SETTINGS,modelSettings()).with(JOINED,joinedFaces).build();}
}
