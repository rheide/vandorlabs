package com.vandorlabs.neoforge.plate;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.persistence.PressurePlateSettings;
import com.vandorlabs.redstone.*;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;

/** Independent analogue channel source. No received signal can latch or press the plate. */
public final class NativePlateEntity extends BlockEntity implements NativeChannelMember {
    public static final ModelProperty<Integer> TEXTURE=new ModelProperty<>();
    public static final String ITEM_SETTINGS="PressurePlateSettings";
    public static final int DEFAULT_TEXTURE=defaultTexture();
    private int texture=DEFAULT_TEXTURE,mode=1,sensitivity,output,range=16,mountRotation;
    private boolean local=true,inert;
    private ChannelList channels=ChannelList.EMPTY;
    private CompoundTag retained=new CompoundTag();private boolean retainedItem;
    private static int defaultTexture(){for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).id().equals("door_inner"))return i;return 0;}
    public NativePlateEntity(BlockPos pos,BlockState state){super(NativePlateBindings.ENTITY,pos,state);}
    public int mountRotation(){return mountRotation;}public int texture(){return texture;}public int mode(){return mode;}public int sensitivity(){return sensitivity;}public int output(){return output;}public boolean localRedstone(){return local;}public boolean inert(){return inert;}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public ChannelList transmittedChannels(){return channels;}
    @Override public int transmissionRange(){return range;}
    @Override public int localSignalLevel(int channel){return inert?0:output;}
    @Override public void setChannelLevel(int level){} // A physical plate is never a latch or receiver.
    @Override public boolean isChannelMemberActive(){return !inert&&getBlockState().getBlock() instanceof NativePlateBlock&&NativeChannelMember.super.isChannelMemberActive();}
    private boolean installed(){return level instanceof ServerLevel server&&server.getServer().isSameThread()&&NativeRedstoneChannels.open(server)&&isChannelMemberActive();}
    public void sample(int physical){if(!installed())return;int next=PressurePlateSettings.output(mode,physical);if(next==output)return;output=next;setChanged();NativeRedstoneChannels.outputChanged(this);level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    public boolean configure(Player actor,int nextTexture,int nextMode,int nextSensitivity){
        if(!installed()||!NativeConfigurationAccess.canEditDialog(actor,this)||!ModernFinishCatalog.validChoice(nextTexture)||nextMode<0||nextMode>2||nextSensitivity<0||nextSensitivity>2)return false;
        texture=ModernFinishCatalog.storedChoice(nextTexture);mode=nextMode;sensitivity=nextSensitivity;setChanged();requestModelDataUpdate();((NativePlateBlock)getBlockState().getBlock()).resample((ServerLevel)level,worldPosition);level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);return true;
    }
    public boolean configureTransmission(Player actor,ChannelList selected,int radius,boolean nativeOutput){
        return NativeConfigurationAccess.canEditDialog(actor,this)&&configureCopiedTransmission(selected,radius,nativeOutput,mountRotation);
    }
    /** Typed mutation after the Copifier target has authorized its loaded member, including distant batches. */
    public boolean configureCopiedTransmission(ChannelList selected,int radius,boolean nativeOutput,int rotation){
        if(!installed()||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,worldPosition)||!TransmissionSettings.validConfiguration(selected,radius))return false;
        NativeRedstoneChannels.batch((ServerLevel)level,()->{NativeRedstoneChannels.unregister(this);channels=selected;range=radius;mountRotation=Math.floorMod(rotation,4);boolean changed=local!=nativeOutput;local=nativeOutput;NativeRedstoneChannels.channelsChanged(this);setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);if(changed){level.updateNeighborsAt(worldPosition,getBlockState().getBlock());level.updateNeighborsAt(worldPosition.below(),getBlockState().getBlock());}});return true;
    }
    @Override public void beforeChannelRegistration(){if(installed())((NativePlateBlock)getBlockState().getBlock()).resample((ServerLevel)level,worldPosition);}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)NativeRedstoneChannels.queueRegistration(this);}
    @Override public void setRemoved(){NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    public CompoundTag configuration(){var tag=new CompoundTag();tag.putInt(PressurePlateSettings.TEXTURE,texture);tag.putInt(PressurePlateSettings.MODE,mode);tag.putInt(PressurePlateSettings.SENSITIVITY,sensitivity);tag.putIntArray("RedstoneChannels",channels.toArray());return tag;}
    private CompoundTag itemData(){var root=new CompoundTag();if(inert)root.put("NativePlateRetained",retained.copy());else root.put(ITEM_SETTINGS,configuration());return root;}
    public void writeItem(ItemStack stack){stack.applyComponents(collectComponents());}
    private boolean readConfiguration(CompoundTag tag,boolean saved){
        for(String key:List.of(PressurePlateSettings.TEXTURE,PressurePlateSettings.MODE,PressurePlateSettings.SENSITIVITY))if(tag.contains(key)&&!tag.contains(key,Tag.TAG_INT))return false;
        int nextTexture=tag.contains(PressurePlateSettings.TEXTURE,Tag.TAG_INT)?tag.getInt(PressurePlateSettings.TEXTURE):DEFAULT_TEXTURE;
        int nextMode=PressurePlateSettings.savedMode(tag.contains(PressurePlateSettings.MODE,Tag.TAG_INT),tag.getInt(PressurePlateSettings.MODE));
        int nextSensitivity=PressurePlateSettings.savedSensitivity(tag.getInt(PressurePlateSettings.SENSITIVITY));
        if(!ModernFinishCatalog.validChoice(nextTexture)||!saved&&(tag.contains(PressurePlateSettings.MODE)&&nextMode!=tag.getInt(PressurePlateSettings.MODE)||tag.contains(PressurePlateSettings.SENSITIVITY)&&nextSensitivity!=tag.getInt(PressurePlateSettings.SENSITIVITY)))return false;
        ChannelList next;try{next=tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY)?ChannelList.of(tag.getIntArray("RedstoneChannels")):ChannelList.of(Math.max(0,tag.getInt("Channel")));}catch(IllegalArgumentException bad){return false;}
        texture=ModernFinishCatalog.storedChoice(nextTexture);mode=nextMode;sensitivity=nextSensitivity;channels=next;return true;
    }
    @Override protected void applyImplicitComponents(DataComponentInput input){super.applyImplicitComponents(input);var data=input.get(DataComponents.CUSTOM_DATA);if(data==null)return;var tag=data.copyTag();if(tag.contains("NativePlateRetained")){retained=tag.contains("NativePlateRetained",Tag.TAG_COMPOUND)?tag.getCompound("NativePlateRetained").copy():tag.copy();retainedItem=true;inert=true;return;}if(tag.contains(ITEM_SETTINGS)&&(!tag.contains(ITEM_SETTINGS,Tag.TAG_COMPOUND)||!readConfiguration(tag.getCompound(ITEM_SETTINGS),false))){retained=tag.copy();retainedItem=true;inert=true;}}
    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder data){super.collectImplicitComponents(data);data.set(DataComponents.CUSTOM_DATA,CustomData.of(itemData()));}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);retainedItem=false;retained=tag.copy();for(String key:List.of("id","x","y","z"))retained.remove(key);
        try{NativePlatformComponents.requireSupported(tag,registries);}catch(IllegalArgumentException unsupported){inert=true;return;}if(tag.contains("NativePlateRetained")){inert=true;if(tag.contains("NativePlateRetained",Tag.TAG_COMPOUND)){retained=tag.getCompound("NativePlateRetained").copy();retainedItem=true;}return;}
        inert=tag.contains("NativePlateSchema")&&(!tag.contains("NativePlateSchema",Tag.TAG_INT)||tag.getInt("NativePlateSchema")!=1)||!readConfiguration(tag,true);
        mountRotation=Math.floorMod(tag.getInt(com.vandorlabs.persistence.SaveSchema.Redstone.MOUNT_ROTATION),4);
        output=PressurePlateSettings.level(tag.getInt(PressurePlateSettings.OUTPUT));
        var setting=tag.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");
        try{channels=TransmissionSettings.readChannels(setting.contains("Channels",Tag.TAG_INT_ARRAY),setting.getIntArray("Channels"),channels);range=TransmissionSettings.readRange(setting.contains("Range",Tag.TAG_INT),setting.getInt("Range"),true);local=TransmissionSettings.readLocal(setting.contains("Local",Tag.TAG_BYTE),setting.getBoolean("Local"),true);}catch(IllegalArgumentException malformed){inert=true;}
        if(!inert)NativePlatformComponents.forgetLoadedFields(retained);
        requestModelDataUpdate();if(level!=null&&!level.isClientSide)NativeRedstoneChannels.queueRegistration(this);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        if(inert){if(retainedItem){super.saveAdditional(tag,registries);tag.put("NativePlateRetained",retained.copy());}else{NativePlatformComponents.clearForInertSave(this);tag.merge(retained);}return;}super.saveAdditional(tag,registries);tag.merge(retained);
        tag.putInt("NativePlateSchema",1);tag.putInt(com.vandorlabs.persistence.SaveSchema.Redstone.MOUNT_ROTATION,mountRotation);tag.merge(configuration());tag.putInt(PressurePlateSettings.OUTPUT,output);
        var forge=tag.getCompound("ForgeData");var all=forge.getCompound(TransmissionSettings.SETTINGS);var main=all.getCompound("main");main.putIntArray("Channels",channels.toArray());main.putInt("Range",range);main.putBoolean("Local",local);all.put("main",main);forge.put(TransmissionSettings.SETTINGS,all);tag.put("ForgeData",forge);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=configuration();tag.putInt(PressurePlateSettings.OUTPUT,output);tag.putBoolean("NativePlateInert",inert);tag.putInt("NativePlateRange",range);tag.putInt("MountRotation",mountRotation);tag.putBoolean("NativePlateLocal",local);return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){readConfiguration(tag,true);output=PressurePlateSettings.level(tag.getInt(PressurePlateSettings.OUTPUT));inert=tag.getBoolean("NativePlateInert");range=tag.getInt("NativePlateRange");mountRotation=Math.floorMod(tag.getInt("MountRotation"),4);local=tag.getBoolean("NativePlateLocal");requestModelDataUpdate();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public ModelData getModelData(){return ModelData.builder().with(TEXTURE,texture).build();}
}
