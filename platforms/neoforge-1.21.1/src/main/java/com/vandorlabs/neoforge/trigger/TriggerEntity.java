package com.vandorlabs.neoforge.trigger;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.persistence.NbtPrimitiveData;
import com.vandorlabs.neoforge.redstone.ModernChannelMember;
import com.vandorlabs.neoforge.redstone.ModernRedstoneChannels;
import com.vandorlabs.persistence.ChannelMembershipData;
import com.vandorlabs.persistence.TriggerState;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Shared trigger artwork policy with native loaded-only analogue channels and inherited housing storage. */
public final class TriggerEntity extends ProgrammableBlockEntity implements ModernChannelMember {
    private volatile TriggerState trigger=TriggerState.defaults();
    private ChannelList channels=ChannelList.EMPTY;
    private volatile int channelLevel,localLevel;
    public TriggerEntity(BlockPos pos,BlockState state){super(TriggerRegistration.ENTITY.get(),pos,state);super.apply(new ProgrammableBlockDialog.State(0,0,false,null,-1));}
    public TriggerState state(){return trigger;}
    public int signalLevel(){return Math.max(channelLevel,localLevel);}
    public int visibleFinish(){return trigger.visible(settings().mainTexture,signalLevel());}
    @Override protected ProgrammableBlockDialog.State modelSettings(){var off=settings();return new ProgrammableBlockDialog.State(visibleFinish(),off.layout,false,null,-1);}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public int localSignalLevel(int channel){
        if(level==null||!level.hasChunkAt(worldPosition))return 0;int result=0;
        for(Direction side:Direction.values()){
            BlockPos neighbor=worldPosition.relative(side);if(!level.hasChunkAt(neighbor))continue;BlockState state=level.getBlockState(neighbor);
            if(state.getBlock().shouldCheckWeakPower(state,level,neighbor,side)){
                for(Direction strongSide:Direction.values()){BlockPos source=neighbor.relative(strongSide);if(level.hasChunkAt(source))result=Math.max(result,level.getBlockState(source).getDirectSignal(level,source,strongSide));}
            }else result=Math.max(result,state.getSignal(level,neighbor,side));
            if(result>=15)return 15;
        }
        return Math.min(15,result);
    }
    @Override public void setChannelLevel(int value){if(channelLevel!=value){channelLevel=value;changed();}}
    public void refreshSignal(){if(level!=null&&!level.isClientSide){localLevel=localSignalLevel(0);if(channels.isEmpty())channelLevel=0;ModernRedstoneChannels.inputChanged(this);changed();}}
    public boolean configure(int off,int on,int low,int medium,boolean bands,int exact,ChannelList channels){
        if(channels==null||!TriggerState.validExact(exact)||!ModernFinishCatalog.validChoice(off)||!ModernFinishCatalog.validChoice(on)||!ModernFinishCatalog.validChoice(low)||!ModernFinishCatalog.validChoice(medium))return false;
        var old=settings();super.apply(new ProgrammableBlockDialog.State(off,old.layout,old.facesEnabled,old.faces(),-1));trigger=new TriggerState(on,bands,exact,low,medium);this.channels=channels;
        if(level!=null&&!level.isClientSide)localLevel=localSignalLevel(0);ModernRedstoneChannels.channelsChanged(this);if(channels.isEmpty())channelLevel=0;changed();return true;
    }
    private void changed(){setChanged();requestModelDataUpdate();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)ModernRedstoneChannels.queueRegistration(this);}
    @Override public void channelLoaded(){refreshSignal();}
    @Override public void setRemoved(){ModernRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){ModernRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);trigger.write(new NbtPrimitiveData(tag));tag.putIntArray("RedstoneChannels",channels.toArray());tag.putInt("RedstoneChannel",channels.first());tag.putInt("ChannelLevel",signalLevel());tag.putBoolean("ChannelSignal",signalLevel()>0);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);trigger=TriggerState.read(new NbtPrimitiveData(tag),ModernFinishCatalog::storedChoice);channels=ChannelMembershipData.read(tag.contains("RedstoneChannels",11)?tag.getIntArray("RedstoneChannels"):null,ChannelList.of(Math.max(0,tag.getInt("RedstoneChannel"))));channelLevel=tag.contains("ChannelLevel",3)?Math.max(0,Math.min(15,tag.getInt("ChannelLevel"))):tag.getBoolean("ChannelSignal")?15:0;localLevel=0;requestModelDataUpdate();}
}
