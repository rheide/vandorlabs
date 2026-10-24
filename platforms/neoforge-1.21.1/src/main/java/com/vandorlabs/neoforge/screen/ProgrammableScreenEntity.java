package com.vandorlabs.neoforge.screen;

import com.vandorlabs.animation.ScreenBehavior;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.redstone.ModernChannelMember;
import com.vandorlabs.neoforge.redstone.ModernRedstoneChannels;
import com.vandorlabs.persistence.PrimitiveData;
import com.vandorlabs.persistence.ScreenData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.ScreenSelectorDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ProgrammableScreenEntity extends BlockEntity implements ModernChannelMember {
    private ScreenData data=new ScreenData("engineering_screen",false,2,true,1,"eng_large","eng_large",-1,false,0,false,0);
    private ChannelList channels=ChannelList.EMPTY;
    private boolean inverted;
    private CompoundTag retainedSettings=new CompoundTag();
    private int sides,signal,surface=-1;
    private final ModernRedstoneScreenContents redstone=new ModernRedstoneScreenContents(this,0);
    public ProgrammableScreenEntity(BlockPos pos,BlockState state){super(ScreenRegistration.TILE.get(),pos,state);}
    public ScreenData data(){return data;}
    public boolean supportsSlot(int slot){return com.vandorlabs.render.ScreenFamilyPolicy.supportsSlot(com.vandorlabs.render.ScreenFamilyPolicy.Kind.FLAT,slot);}
    public int rowCapacity(int slot){return com.vandorlabs.render.ScreenFamilyPolicy.maxRows(com.vandorlabs.render.ScreenFamilyPolicy.Kind.FLAT,slot);}
    public int surface(){return redstone.enabled()?com.vandorlabs.redstone.RedstoneScreenPolicy.REDSTONE_SURFACE:surface;}
    public ModernRedstoneScreenContents redstone(){return redstone;}
    int getHousingTexture(){return data.housingTexture;}
    void setHousingTexture(int texture){data=new ScreenData(data.selectedScreen,data.redstoneEnabled,data.displayMode,data.framed,data.animationSpeedIndex,data.inputPanel,data.secondaryInputPanel,data.wallPosition,data.smallInput,data.redstoneChannel,data.channelSignal,ModernFinishCatalog.storedChoice(texture));sync();}
    void redstoneChanged(){sync();}
    private int emittedLight(){return redstone.enabled()?11:effectiveMode()==0?0:11;}
    public int sides(){return sides;}
    public boolean inverted(){return inverted;}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public int localSignalLevel(int channel){
        if(level==null)return 0;int power=0;
        for(Direction direction:Direction.values()){
            BlockPos neighbor=worldPosition.relative(direction);
            if(level.hasChunkAt(neighbor))power=Math.max(power,level.getSignal(neighbor,direction));
        }
        return power;
    }
    @Override public void setChannelLevel(int power){if(signal!=power||getBlockState().getValue(ProgrammableScreenBlock.LIGHT)!=emittedLight()){signal=power;sync();}}
    public void refreshSignal(){if(level!=null&&!level.isClientSide){ModernRedstoneChannels.inputChanged(this);sync();}}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)ModernRedstoneChannels.queueRegistration(this);}
    @Override public void channelLoaded(){redstone.finishLoading();}
    @Override public void setRemoved(){redstone.unregister();if(level!=null&&!level.isClientSide)ModernRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){redstone.unregister();if(level!=null&&!level.isClientSide)ModernRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    public int effectiveMode(){boolean powered=signal>0||localSignalLevel(0)>0;return ScreenBehavior.effectiveMode(data.displayMode,data.redstoneEnabled,data.redstoneEnabled&&inverted?!powered:powered);}
    public ScreenSelectorDialog.State dialogState(int tab){return new ScreenSelectorDialog.State(false,tab,data.displayMode,data.animationSpeedIndex,!data.redstoneEnabled?0:inverted?2:1,sides,data.framed,ScreenArtwork.find(data.selectedScreen).paired());}
    public void configure(String artwork,ScreenSelectorDialog.State settings,ChannelList channels,int housing,int surface){
        if(surface==com.vandorlabs.redstone.RedstoneScreenPolicy.REDSTONE_SURFACE){redstone.setEnabled(true);if(this.surface==com.vandorlabs.redstone.RedstoneScreenPolicy.VEHICLE_SURFACE)this.surface=-1;}else{redstone.setEnabled(false);this.surface=surface;}
        this.channels=channels;this.inverted=settings.redstoneInverted();this.sides=settings.sides;
        data=new ScreenData(artwork,settings.redstoneEnabled(),settings.mode,settings.framed,settings.speed,data.inputPanel,data.secondaryInputPanel,-1,false,channels.first(),signal>0,ModernFinishCatalog.storedChoice(housing));
        if(level!=null&&!level.isClientSide)ModernRedstoneChannels.channelsChanged(this);
        sync();
    }
    private void sync(){
        setChanged();if(level==null)return;
        if(!level.isClientSide){BlockState next=getBlockState().setValue(ProgrammableScreenBlock.LIGHT,emittedLight());if(next!=getBlockState())level.setBlock(worldPosition,next,3);}
        level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);level.getLightEngine().checkBlock(worldPosition);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.merge(retainedSettings);data.write(new Tags(tag));tag.putBoolean("RedstoneInverted",inverted);tag.putInt("SurfaceTextureLayout",sides);tag.putIntArray("RedstoneChannels",channels.toArray());tag.putInt("ChannelLevel",signal);tag.putInt("HousingTextureVersion",1);tag.putInt("PrimarySurfaceTexture",surface);tag.put("RedstonePrimary",redstone.save(new CompoundTag()));
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);retainedSettings=tag.copy();for(String key:java.util.List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retainedSettings.remove(key);data=ScreenData.read(new Tags(tag),"engineering_screen","eng_large",value->value!=null&&!value.isEmpty());
        int main=com.vandorlabs.ui.ProgrammableBlockDialog.savedMainTexture(data.housingTexture,tag.contains("HousingTextureVersion"));
        data=new ScreenData(data.selectedScreen,data.redstoneEnabled,data.displayMode,data.framed,data.animationSpeedIndex,data.inputPanel,data.secondaryInputPanel,data.wallPosition,data.smallInput,data.redstoneChannel,data.channelSignal,ModernFinishCatalog.storedChoice(main));
        surface=tag.contains("PrimarySurfaceTexture")?tag.getInt("PrimarySurfaceTexture"):-1;inverted=tag.getBoolean("RedstoneInverted");sides=Math.max(0,Math.min(2,tag.getInt("SurfaceTextureLayout")));signal=Math.max(0,Math.min(15,tag.getInt("ChannelLevel")));
        redstone.load(tag.getCompound("RedstonePrimary"));
        int[] saved=tag.getIntArray("RedstoneChannels");try{channels=saved.length==0?ChannelList.of(data.redstoneChannel):ChannelList.of(saved);}catch(IllegalArgumentException invalid){channels=ChannelList.EMPTY;}
    }
    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components){
        super.collectImplicitComponents(components);if(level==null)return;
        CompoundTag configuration=saveWithId(level.registryAccess());for(String key:java.util.List.of("x","y","z","ChannelLevel","channelSignal","JoinedSignalLevel","LightGroupPowered"))configuration.remove(key);
        configuration.put("RedstonePrimary",redstone.configuration());
        components.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(configuration));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveWithoutMetadata(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    private record Tags(CompoundTag tag) implements PrimitiveData {
        public boolean contains(String k){return tag.contains(k);}public String getString(String k){return tag.getString(k);}public int getInt(String k){return tag.getInt(k);}public long getLong(String k){return tag.getLong(k);}public double getDouble(String k){return tag.getDouble(k);}public boolean getBoolean(String k){return tag.getBoolean(k);}
        public void putString(String k,String v){tag.putString(k,v);}public void putInt(String k,int v){tag.putInt(k,v);}public void putLong(String k,long v){tag.putLong(k,v);}public void putDouble(String k,double v){tag.putDouble(k,v);}public void putBoolean(String k,boolean v){tag.putBoolean(k,v);}
    }
}
