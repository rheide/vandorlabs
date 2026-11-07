package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.TriggerState;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.neoforge.redstone.NativeChannelMember;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Static Trigger artwork, with an immutable render projection published on the game thread. */
public final class NativeTriggerBlockEntity extends ProgrammableBlockEntity implements NativeChannelMember {
    private TriggerState trigger=TriggerState.read(new ModernHousingTags(new CompoundTag()),ModernFinishCatalog::storedChoice);
    private ChannelList channels=ChannelList.EMPTY;
    private int channelLevel,localLevel;
    private boolean localDirty=true;
    private volatile ProgrammableBlockDialog.State visual=triggerDefaults();
    public NativeTriggerBlockEntity(BlockPos pos,BlockState state){super(VandorLabsBootstrap.TRIGGER_ENTITY.get(),pos,state,triggerDefaults());}
    public static ProgrammableBlockDialog.State triggerDefaults(){return new ProgrammableBlockDialog.State(0,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1);}
    public TriggerState triggerState(){return trigger;}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public BlockEntity channelBlockEntity(){return this;}
    public int signalLevel(){return Math.max(channelLevel,localLevel);}
    @Override protected ProgrammableBlockDialog.State modelSettings(){return visual;}
    private void publishVisual(){
        int slot=TriggerState.visibleSlot(trigger.bands,trigger.exact,signalLevel());
        int main=slot==0?settings().mainTexture:slot==1?trigger.low:slot==2?trigger.medium:trigger.on;
        int[] faces={trigger.sides,trigger.sides,main,trigger.sides,trigger.sides,trigger.sides};
        visual=new ProgrammableBlockDialog.State(main,settings().layout,trigger.frontOnly,faces,-1);
        refresh();
    }
    /** No inventory/platform serialization is needed to capture Trigger choices. */
    public CompoundTag copiedSettings(){var fields=copiedHousingFields();trigger.write(new ModernHousingTags(fields));fields.putIntArray("RedstoneChannels",channels.toArray());fields.putInt("RedstoneChannel",channels.first());return fields;}
    /** Caller has authorized the selected installed target before this typed copied commit. */
    public boolean configureCopied(CompoundTag values){
        if(!(level instanceof net.minecraft.server.level.ServerLevel world)||!world.getServer().isSameThread()||!NativeRedstoneChannels.open(world)||isRemoved()||!level.hasChunkAt(worldPosition)||level.getBlockEntity(worldPosition)!=this)return false;
        com.vandorlabs.neoforge.copifier.NativeCopifierTriggerEdit.Plan plan;try{plan=com.vandorlabs.neoforge.copifier.NativeCopifierTriggerEdit.prepare(copiedSettings(),values);}catch(IllegalArgumentException refused){return false;}if(!plan.applicable())return false;
        var metadata=plan.metadata();if(!installCopiedMetadata(metadata.frames(),metadata.channels(),metadata.transmissionRange(),metadata.localRedstone(),metadata.transmittedChannels()))return false;
        NativeRedstoneChannels.batch(world,()->{boolean changed=!channels.equals(metadata.channels());trigger=plan.trigger();channels=metadata.channels();updateSettings(new ProgrammableBlockDialog.State(plan.off(),settings().layout,settings().facesEnabled,settings().faces(),-1));if(changed)NativeRedstoneChannels.channelsChanged(this);com.vandorlabs.neoforge.redstone.NativeSignalUpdateBatch.afterSignals(this,this::publishVisual);});return true;
    }
    public boolean configure(int off,TriggerState next,ChannelList bank){
        if(bank==null||next==null||next.exact< -1||next.exact>15||!TriggerState.validArtwork(next.artworkModes(),next.artworkSpeeds()))return false;
        for(int choice:new int[]{off,next.on,next.low,next.medium,next.sides})if(!ModernFinishCatalog.validChoice(choice))return false;
        boolean changed=!channels.equals(bank);
        trigger=next;channels=bank;
        super.apply(new ProgrammableBlockDialog.State(off,settings().layout,settings().facesEnabled,settings().faces(),-1));
        publishVisual();if(changed&&level!=null&&!level.isClientSide)NativeRedstoneChannels.channelsChanged(this);
        return true;
    }
    @Override public void setChannelLevel(int value){
        int bounded=Math.max(0,Math.min(15,value));if(channelLevel==bounded)return;
        channelLevel=bounded;publishVisual();
    }
    public void localInputChanged(){localDirty=true;}
    /** Called by the installed tile's ordinary world ticker, after chunk loading. */
    public void tickLocalInput(){
        if(!localDirty||level==null||level.isClientSide||isRemoved()||!level.hasChunkAt(worldPosition)||level.getBlockEntity(worldPosition)!=this)return;
        localDirty=false;int result=0;
        for(Direction side:Direction.values()){
            BlockPos neighbor=worldPosition.relative(side);if(!level.hasChunkAt(neighbor))continue;
            BlockState state=level.getBlockState(neighbor);
            if(state.shouldCheckWeakPower(level,neighbor,side)){
                for(Direction strong:Direction.values()){
                    BlockPos source=neighbor.relative(strong);
                    if(level.hasChunkAt(source))result=Math.max(result,level.getBlockState(source).getDirectSignal(level,source,strong));
                }
            }else result=Math.max(result,state.getSignal(level,neighbor,side));
            if(result>=15)break;
        }
        result=Math.min(15,result);if(localLevel!=result){localLevel=result;publishVisual();}
    }
    @Override public void onLoad(){super.onLoad();localDirty=true;if(level!=null&&!level.isClientSide)NativeRedstoneChannels.queueRegistration(this);}
    @Override public void setRemoved(){NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        trigger=TriggerState.read(new ModernHousingTags(tag),ModernFinishCatalog::storedChoice);
        ChannelList fallback=ChannelList.of(Math.max(0,tag.getInt("RedstoneChannel")));
        try{channels=tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY)?ChannelList.of(tag.getIntArray("RedstoneChannels")):fallback;}catch(IllegalArgumentException malformed){channels=fallback;}
        channelLevel=tag.contains("ChannelLevel",Tag.TAG_INT)?Math.max(0,Math.min(15,tag.getInt("ChannelLevel"))):tag.getBoolean("ChannelSignal")?15:0;
        localLevel=Math.max(0,Math.min(15,tag.getInt("TriggerLocalLevel")));localDirty=true;
        // Publish only local fields while loading; membership and neighbor reads wait for world ticks.
        int slot=TriggerState.visibleSlot(trigger.bands,trigger.exact,signalLevel());
        int main=slot==0?settings().mainTexture:slot==1?trigger.low:slot==2?trigger.medium:trigger.on;
        visual=new ProgrammableBlockDialog.State(main,settings().layout,trigger.frontOnly,new int[]{trigger.sides,trigger.sides,main,trigger.sides,trigger.sides,trigger.sides},-1);
        requestModelDataUpdate();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);trigger.write(new ModernHousingTags(tag));
        tag.putIntArray("RedstoneChannels",channels.toArray());tag.putInt("RedstoneChannel",channels.first());
        tag.putInt("ChannelLevel",channelLevel);tag.putBoolean("ChannelSignal",channelLevel>0);tag.putInt("TriggerLocalLevel",localLevel);
    }
}
