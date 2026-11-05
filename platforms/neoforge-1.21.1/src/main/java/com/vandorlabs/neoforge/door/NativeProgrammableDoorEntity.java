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
public class NativeProgrammableDoorEntity extends BlockEntity {
    public static final ModelProperty<NativeDoorSettings> SETTINGS=new ModelProperty<>();
    private final boolean large;
    private volatile NativeDoorSettings settings;
    private CompoundTag retained=new CompoundTag();
    private ChannelList channels=ChannelList.EMPTY;
    private boolean channelSignal,securityToggled,legacyMotionPending;
    public NativeProgrammableDoorEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,boolean large,boolean slidingModel){super(type,pos,state);this.large=large;settings=NativeDoorSettings.fresh(large,slidingModel);}
    public final boolean large(){return large;}
    public final NativeDoorSettings settings(){return settings;}
    public final ChannelList getRedstoneChannels(){return channels;}
    public final boolean channelSignal(){return channelSignal;}
    public final boolean securityToggled(){return securityToggled;}
    public final boolean legacyMotionPending(){return legacyMotionPending;}
    @Override public ModelData getModelData(){return ModelData.builder().with(SETTINGS,settings).build();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);retained=tag.copy();for(String key:java.util.List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retained.remove(key);
        settings=NativeDoorSettings.read(tag);ChannelList fallback=ChannelList.of(Math.max(0,tag.getInt("RedstoneChannel")));
        try{channels=tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY)?ChannelList.of(tag.getIntArray("RedstoneChannels")):fallback;}catch(IllegalArgumentException malformed){channels=fallback;}
        channelSignal=tag.getBoolean("RedstoneSignal");securityToggled=tag.getBoolean("DoorSecurityToggled");legacyMotionPending=!tag.contains("SpaceDoorSchema");requestModelDataUpdate();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.merge(retained);writePublicSettings(tag);tag.putBoolean("DoorSecurityToggled",securityToggled);}
    private void writePublicSettings(CompoundTag tag){settings.write(tag);tag.putIntArray("RedstoneChannels",channels.toArray());new RedstoneData.Member(channels.first(),channelSignal).write(new ModernHousingTags(tag));}
    /** Explicit projection: opaque retained data, reader inventories and foreign capabilities never enter a menu/update packet. */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();writePublicSettings(tag);tag.putBoolean("DoorSecurityToggled",securityToggled);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    /** Configured items contain choices/channels only, matching legacy itemSettings rather than a world snapshot. */
    public final CompoundTag itemSettings(){CompoundTag tag=new CompoundTag();settings.write(tag);tag.putInt("SpaceDoorChannel",channels.first());tag.putIntArray("RedstoneChannels",channels.toArray());return tag;}
    final boolean accepts(NativeDoorEdit edit){return edit!=null&&edit.accepts(large);}
    final boolean stage(NativeDoorEdit edit,ChannelList next){
        NativeDoorSettings updated=edit.apply(settings);boolean changed=!settings.same(updated)||!channels.equals(next);
        if(!changed)return false;
        if(settings.securityMode()!=updated.securityMode()||settings.securityAllowed()!=updated.securityAllowed())securityToggled=false;
        int previousBehavior=NativeDoorSettings.securityBehavior(settings.securityMode());settings=updated;channels=next;legacyMotionPending=false;
        for(String key:java.util.List.of("DoorKeyReader","DoorRearKeyReader"))if(retained.contains(key,Tag.TAG_COMPOUND)){
            CompoundTag reader=retained.getCompound(key);boolean any=updated.securityAllowed()==127;int behavior=NativeDoorSettings.securityBehavior(updated.securityMode());
            if(NativeDoorSettings.readerCompany(reader)!=updated.securityAllowed()||previousBehavior!=behavior){reader.putLong("TokenPulseUntil",0);reader.putBoolean("TokenToggled",false);}
            reader.putBoolean("AnyToken",any);reader.putInt("AllowedTokens",updated.securityAllowed());reader.putInt("TokenBehavior",behavior);
        }
        return true;
    }
    final void publish(){setChanged();requestModelDataUpdate();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
}
