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
    private ScreenData data=new ScreenData("engineering_screen",false,2,true,1,InputArtwork.DEFAULT,InputArtwork.DEFAULT,-1,false,0,false,0);
    private ChannelList channels=ChannelList.EMPTY;
    private boolean inverted;
    private CompoundTag retainedSettings=new CompoundTag();
    private int sides=1,signal,surface=-1,secondarySurface=-1;
    private final ModernRedstoneScreenContents redstone=new ModernRedstoneScreenContents(this,0);
    private final ModernRedstoneScreenContents secondaryRedstone=new ModernRedstoneScreenContents(this,1);
    public ProgrammableScreenEntity(BlockPos pos,BlockState state){super(ScreenRegistration.TILE.get(),pos,state);}
    public ScreenData data(){return data;}
    public com.vandorlabs.render.ScreenFamilyPolicy.Kind family(){return ((ProgrammableScreenBlock)getBlockState().getBlock()).family();}
    public net.minecraft.core.Direction facing(){return ((ProgrammableScreenBlock)getBlockState().getBlock()).facing(getBlockState());}
    public boolean keyboard(){return ((ProgrammableScreenBlock)getBlockState().getBlock()).keyboard(getBlockState());}
    public boolean upper(){return ((ProgrammableScreenBlock)getBlockState().getBlock()).upper(getBlockState());}
    public boolean geometryInverted(){return ((ProgrammableScreenBlock)getBlockState().getBlock()).inverted(getBlockState());}
    public boolean ceilingMounted(){return retainedSettings.getBoolean("CeilingMounted");}
    public int ceilingPosition(){return retainedSettings.contains("CeilingPosition",3)?Math.max(0,Math.min(2,retainedSettings.getInt("CeilingPosition"))):family()==com.vandorlabs.render.ScreenFamilyPolicy.Kind.FULL_INPUT||family()==com.vandorlabs.render.ScreenFamilyPolicy.Kind.HALF_CONSOLE?1:2;}
    public int wallPosition(){return data.wallPosition<0?upper()?2:0:data.wallPosition;}
    public boolean supportsSlot(int slot){return com.vandorlabs.render.ScreenFamilyPolicy.supportsSlot(family(),slot);}
    public int rowCapacity(int slot){return com.vandorlabs.render.ScreenFamilyPolicy.maxRows(family(),slot);}
    public int surface(){return surface(0);}
    public int surface(int slot){return redstone(slot).enabled()?com.vandorlabs.redstone.RedstoneScreenPolicy.REDSTONE_SURFACE:slot==1?secondarySurface:surface;}
    public ModernRedstoneScreenContents redstone(){return redstone;}
    public ModernRedstoneScreenContents redstone(int slot){if(!supportsSlot(slot))throw new IllegalArgumentException("Unsupported screen slot "+slot);return slot==1?secondaryRedstone:redstone;}
    public boolean hasOverlay(){return redstone.enabled()||supportsSlot(1)&&secondaryRedstone.enabled();}
    public boolean inputSurface(int slot){return family()==com.vandorlabs.render.ScreenFamilyPolicy.Kind.CONSOLE?slot==1:family()==com.vandorlabs.render.ScreenFamilyPolicy.Kind.HALF_INPUT||family()==com.vandorlabs.render.ScreenFamilyPolicy.Kind.HALF_CONSOLE||family()==com.vandorlabs.render.ScreenFamilyPolicy.Kind.DIAGONAL_HALF;}
    public String inputArtwork(int slot){return slot==1&&family()==com.vandorlabs.render.ScreenFamilyPolicy.Kind.HALF_CONSOLE?data.secondaryInputPanel:data.inputPanel;}
    public ScreenSelectorDialog.Profile dialogProfile(){return switch(family()){case CONSOLE->ScreenSelectorDialog.Profile.CONSOLE;case HALF_CONSOLE->ScreenSelectorDialog.Profile.HALF_CONSOLE;case HALF_INPUT->ScreenSelectorDialog.Profile.INPUT;case DIAGONAL_HALF->ScreenSelectorDialog.Profile.DIAGONAL_HALF;default->ScreenSelectorDialog.Profile.SCREEN;};}
    int getHousingTexture(){return data.housingTexture;}
    void setHousingTexture(int texture){data=new ScreenData(data.selectedScreen,data.redstoneEnabled,data.displayMode,data.framed,data.animationSpeedIndex,data.inputPanel,data.secondaryInputPanel,data.wallPosition,data.smallInput,data.redstoneChannel,data.channelSignal,ModernFinishCatalog.storedChoice(texture));sync();}
    void redstoneChanged(){sync();}
    private int emittedLight(){return !((ProgrammableScreenBlock)getBlockState().getBlock()).emitsLight()?0:hasOverlay()?11:effectiveMode()==0?0:11;}
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
    @Override public void channelLoaded(){redstone.finishLoading();if(supportsSlot(1))secondaryRedstone.finishLoading();}
    @Override public void setRemoved(){redstone.unregister();secondaryRedstone.unregister();if(level!=null&&!level.isClientSide)ModernRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){redstone.unregister();secondaryRedstone.unregister();if(level!=null&&!level.isClientSide)ModernRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    public int effectiveMode(){boolean powered=signal>0||localSignalLevel(0)>0;return ScreenBehavior.effectiveMode(data.displayMode,data.redstoneEnabled,data.redstoneEnabled&&inverted?!powered:powered);}
    public ScreenSelectorDialog.State dialogState(int tab){
        boolean console=dialogProfile().tabs()==3,input=tab<(console?2:1)&&inputSurface(tab);
        return new ScreenSelectorDialog.State(console,tab,data.displayMode,data.animationSpeedIndex,!data.redstoneEnabled?0:inverted?2:1,sides,input?InputArtwork.framed(inputArtwork(tab)):data.framed,input?InputArtwork.find(inputArtwork(tab)).paired():ScreenArtwork.find(data.selectedScreen).paired(),data.smallInput);
    }
    public void configure(String artwork,ScreenSelectorDialog.State settings,ChannelList channels,int housing,int primary){
        configureFamily(artwork,data.inputPanel,data.secondaryInputPanel,settings,channels,housing,primary,secondarySurface);
    }
    public void configureFamily(String artwork,String input,String secondaryInput,ScreenSelectorDialog.State settings,ChannelList channels,int housing,int primary,int secondary){
        setSurface(0,primary);if(supportsSlot(1))setSurface(1,secondary);
        this.channels=channels;this.inverted=settings.redstoneInverted();this.sides=settings.sides;
        data=new ScreenData(artwork,settings.redstoneEnabled(),settings.mode,settings.framed,settings.speed,input,secondaryInput,data.wallPosition,settings.smallInput,channels.first(),signal>0,ModernFinishCatalog.storedChoice(housing));
        if(level!=null&&!level.isClientSide)ModernRedstoneChannels.channelsChanged(this);sync();
    }
    private void setSurface(int slot,int choice){
        ModernRedstoneScreenContents controls=redstone(slot);
        int current=slot==1?secondarySurface:surface;
        if(choice==com.vandorlabs.redstone.RedstoneScreenPolicy.REDSTONE_SURFACE){controls.setEnabled(true);if(current==com.vandorlabs.redstone.RedstoneScreenPolicy.VEHICLE_SURFACE)current=-1;}
        else{controls.setEnabled(false);current=choice>=0?ModernFinishCatalog.storedChoice(choice):choice==com.vandorlabs.redstone.RedstoneScreenPolicy.VEHICLE_SURFACE?choice:-1;}
        if(slot==1)secondarySurface=current;else surface=current;
    }
    public void placementMount(boolean ceiling,int ceilingPosition,int wallPosition,boolean replaceCeiling){
        if(replaceCeiling||ceiling)retainedSettings.putBoolean("CeilingMounted",ceiling);
        if(ceiling)retainedSettings.putInt("CeilingPosition",Math.max(0,Math.min(2,ceilingPosition)));
        if(wallPosition>=0)data=new ScreenData(data.selectedScreen,data.redstoneEnabled,data.displayMode,data.framed,data.animationSpeedIndex,data.inputPanel,data.secondaryInputPanel,Math.max(0,Math.min(2,wallPosition)),data.smallInput,data.redstoneChannel,data.channelSignal,data.housingTexture);
        sync();
    }
    private void sync(){
        setChanged();if(level==null)return;
        if(!level.isClientSide){BlockState next=getBlockState().setValue(ProgrammableScreenBlock.LIGHT,emittedLight());if(next!=getBlockState())level.setBlock(worldPosition,next,3);}
        level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);level.getLightEngine().checkBlock(worldPosition);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.merge(retainedSettings);data.write(new Tags(tag));tag.putBoolean("RedstoneInverted",inverted);tag.putInt("SurfaceTextureLayout",sides);tag.putBoolean("SurfaceTileSides",sides==1);tag.putIntArray("RedstoneChannels",channels.toArray());tag.putInt("ChannelLevel",signal);tag.putInt("HousingTextureVersion",1);tag.putInt("PrimarySurfaceTexture",surface);tag.putInt("SecondarySurfaceTexture",secondarySurface);tag.put("RedstonePrimary",redstone.save(new CompoundTag()));if(supportsSlot(1))tag.put("RedstoneSecondary",secondaryRedstone.save(new CompoundTag()));
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);retainedSettings=tag.copy();for(String key:java.util.List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retainedSettings.remove(key);data=ScreenData.read(new Tags(tag),"engineering_screen",InputArtwork.DEFAULT,InputArtwork::valid);
        int main=com.vandorlabs.ui.ProgrammableBlockDialog.savedMainTexture(data.housingTexture,tag.contains("HousingTextureVersion",3));
        data=new ScreenData(data.selectedScreen,data.redstoneEnabled,data.displayMode,data.framed,data.animationSpeedIndex,data.inputPanel,data.secondaryInputPanel,data.wallPosition,data.smallInput,data.redstoneChannel,data.channelSignal,ModernFinishCatalog.storedChoice(main));
        surface=savedSurface(tag,"PrimarySurfaceTexture");secondarySurface=savedSurface(tag,"SecondarySurfaceTexture");inverted=tag.getBoolean("RedstoneInverted");sides=com.vandorlabs.ui.ProgrammableBlockDialog.savedTextureLayout(tag.getInt("SurfaceTextureLayout"),tag.contains("SurfaceTextureLayout",3),!tag.contains("SurfaceTileSides",1)||tag.getBoolean("SurfaceTileSides"));signal=tag.contains("ChannelLevel",3)?Math.max(0,Math.min(15,tag.getInt("ChannelLevel"))):data.channelSignal?15:0;
        redstone.load(tag.getCompound("RedstonePrimary"));secondaryRedstone.load(tag.getCompound("RedstoneSecondary"));
        int[] saved=tag.getIntArray("RedstoneChannels");try{channels=saved.length==0?ChannelList.of(data.redstoneChannel):ChannelList.of(saved);}catch(IllegalArgumentException invalid){channels=ChannelList.EMPTY;}
    }
    private static int savedSurface(CompoundTag tag,String key){if(!tag.contains(key,3))return -1;int choice=tag.getInt(key);return choice>=0?ModernFinishCatalog.storedChoice(choice):choice==com.vandorlabs.redstone.RedstoneScreenPolicy.VEHICLE_SURFACE?choice:-1;}
    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components){
        super.collectImplicitComponents(components);if(level==null)return;
        CompoundTag configuration=saveWithId(level.registryAccess());for(String key:java.util.List.of("x","y","z","ChannelLevel","channelSignal","JoinedSignalLevel","LightGroupPowered"))configuration.remove(key);
        configuration.put("RedstonePrimary",redstone.configuration());if(supportsSlot(1))configuration.put("RedstoneSecondary",secondaryRedstone.configuration());
        components.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(configuration));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveWithoutMetadata(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    private record Tags(CompoundTag tag) implements PrimitiveData {
        public boolean contains(String k){return tag.contains(k);}public String getString(String k){return tag.getString(k);}public int getInt(String k){return tag.getInt(k);}public long getLong(String k){return tag.getLong(k);}public double getDouble(String k){return tag.getDouble(k);}public boolean getBoolean(String k){return tag.getBoolean(k);}
        public void putString(String k,String v){tag.putString(k,v);}public void putInt(String k,int v){tag.putInt(k,v);}public void putLong(String k,long v){tag.putLong(k,v);}public void putDouble(String k,double v){tag.putDouble(k,v);}public void putBoolean(String k,boolean v){tag.putBoolean(k,v);}
    }
}
