package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.redstone.*;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Unregistered standalone host. Physical ownership never enters its public projection or configured pick. */
public class NativeCredentialReaderEntity extends BlockEntity implements NativeChannelMember {
    private static final List<String> READER_FIELDS=List.of("TokenLogo","TokenRejectedUntil","ChipSlot","AnyToken","AllowedTokens","TokenBehavior","TokenPulseUntil","TokenToggled","TokenToggleApplied","TokenInsertStarted","TokenInsertionSequence","TokenReturnTo","TokenReturnToMost","TokenReturnToLeast","InsertedToken");
    public record Visual(ControlMountSettings mount,int rotation,int allowed,NativeTokenReader.Visual contents,NativeTokenReader.Motion motion,boolean inert){}
    private final boolean chip;
    private NativeTokenReader reader;
    private CompoundTag retained=new CompoundTag();
    private ChannelList channels=ChannelList.EMPTY,transmission=ChannelList.EMPTY;
    private ControlMountSettings mount=ControlMountSettings.of(0,0,0);
    private int rotation,range=16;
    private boolean inert,local=true,pending=true,publishing,recoveryTaken,initialized;
    private volatile Visual visual;
    public NativeCredentialReaderEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,boolean chip){super(type,pos,state);this.chip=chip;reader=newReader();project();}
    private NativeTokenReader newReader(){return new NativeTokenReader(new NativeTokenReader.Host(){
        public BlockEntity owner(){return NativeCredentialReaderEntity.this;}
        public boolean usable(ServerPlayer player){return NativeCredentialReaderEntity.this.usable(player);}
        public void changed(){publish();}
        public void schedule(int ticks){if(level instanceof ServerLevel server&&!inert)server.scheduleTick(worldPosition,getBlockState().getBlock(),Math.max(1,ticks));}
        public boolean chip(boolean ignored){return chip;}
    },chip);}
    public boolean chip(){return chip;}
    public boolean inert(){return inert;}
    public Visual visual(){return visual;}
    public ControlMountSettings mountSettings(){return mount;}
    public int mountRotation(){return rotation;}
    public boolean usable(ServerPlayer player){return !inert&&level instanceof ServerLevel server&&server.getServer().isSameThread()&&NativeConfigurationAccess.canEditDialog(player,this);}
    public int outputLevel(){return inert?0:reader.outputLevel(level==null?0:level.getGameTime());}
    public int physicalLevel(){return local?outputLevel():0;}
    public boolean interact(ServerPlayer player,InteractionHand hand){return usable(player)&&reader.interact(player,hand,false);}
    public boolean holdingCredential(ServerPlayer player,InteractionHand hand){return reader.holdingCredential(player,hand,false);}
    public ItemStack takeForDrop(){return inert?ItemStack.EMPTY:reader.takeForDrop();}
    public ItemStack recoveryForRemoval(net.minecraft.world.item.Item item){if(!inert||recoveryTaken||retained.isEmpty())return ItemStack.EMPTY;recoveryTaken=true;var result=new ItemStack(item);var tag=new CompoundTag();tag.put("NativeReaderRecovery",retained.copy());result.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));return result;}
    public boolean configure(ServerPlayer player,boolean any,int allowed,int behavior,ControlMountSettings next){
        if(publishing||!usable(player)||next==null||!ControlMountSettings.valid(next.height,next.tilt,next.direction)||behavior<0||behavior>3)return false;
        boolean changed=reader.configureLocal(any,allowed,behavior)||mount.height!=next.height||mount.tilt!=next.tilt||mount.direction!=next.direction;
        mount=next;if(changed)publish();return true;
    }
    public CompoundTag configuration(){var out=new CompoundTag();out.putInt("Channel",channels.first());out.putIntArray("RedstoneChannels",channels.toArray());ControlMountSettings.write(new ModernHousingTags(out),mount.height,mount.tilt,mount.direction);return out;}
    public void place(CompoundTag choices,int ownRotation){if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!isChannelMemberActive())return;channels=readChannels(choices);transmission=channels;rotation=ownRotation&3;mount=ControlMountSettings.read(new ModernHousingTags(choices),0,0,0);publish();NativeRedstoneChannels.channelsChanged(this);}
    public void scheduledTick(){if(!inert)reader.scheduledTick();}
    private void project(){long now=level==null?0:level.getGameTime();visual=new Visual(mount,rotation,reader.effectiveAllowed(),reader.visual(now),reader.motion(now),inert);}
    private void publish(){
        if(publishing){project();return;}publishing=true;try{
            initialized=true;project();setChanged();if(!(level instanceof ServerLevel server)||!isChannelMemberActive())return;
            BlockState state=getBlockState();boolean on=outputLevel()>0;if(state.hasProperty(NativeMountedSwitchBlock.ON)&&state.getValue(NativeMountedSwitchBlock.ON)!=on)server.setBlock(worldPosition,state.setValue(NativeMountedSwitchBlock.ON,on),2);
            server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);NativeRedstoneChannels.outputChanged(this);
            for(Direction face:Direction.values()){BlockPos neighbor=worldPosition.relative(face);if(!server.hasChunkAt(neighbor))continue;server.neighborChanged(neighbor,getBlockState().getBlock(),worldPosition);if(server.getBlockState(neighbor).isRedstoneConductor(server,neighbor))for(Direction side:Direction.values()){BlockPos target=neighbor.relative(side);if(server.hasChunkAt(target))server.neighborChanged(target,getBlockState().getBlock(),neighbor);}}
        }finally{publishing=false;}
    }
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public boolean isChannelMemberActive(){return !inert&&NativeChannelMember.super.isChannelMemberActive();}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public ChannelList transmittedChannels(){return transmission;}
    @Override public int transmissionRange(){return range;}
    @Override public int localSignalLevel(int channel){return outputLevel();}
    @Override public void setChannelLevel(int ignored){} // A received signal cannot become a local reader output.
    @Override public void beforeChannelRegistration(){if(!pending||!isChannelMemberActive())return;pending=false;reader.resume(true);if(isChannelMemberActive())publish();}
    @Override public void onLoad(){super.onLoad();pending=true;if(level instanceof ServerLevel)NativeRedstoneChannels.queueRegistration(this);}
    @Override public void setRemoved(){NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    private static ChannelList readChannels(CompoundTag tag){ChannelList fallback=ChannelList.of(Math.max(0,tag.getInt("Channel")));if(!tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))return fallback;try{return ChannelList.of(tag.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException invalid){return fallback;}}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        CompoundTag original=tag.copy();if(tag.getBoolean("ReaderProjection")){retained=original;inert=true;reader=newReader();applyProjection(tag);return;}try(var guard=NativePlatformComponents.guardLoad(this)){
            if(tag.contains("NativeReaderRecovery"))throw new IllegalArgumentException("Reader recovery ownership");NativePlatformComponents.requireSupported(tag,registries);super.loadAdditional(tag,registries);guard.requireSuccessful();
            NativeTokenReader next=newReader();CompoundTag data=new CompoundTag();for(String key:READER_FIELDS)if(tag.contains(key))data.put(key,tag.get(key).copy());next.read(data,registries);if(next.opaqueOccupied())throw new IllegalArgumentException("Unresolved physical reader owner");
            channels=readChannels(tag);var config=tag.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");transmission=TransmissionSettings.readChannels(config.contains("Channels",Tag.TAG_INT_ARRAY),config.getIntArray("Channels"),channels);range=TransmissionSettings.readRange(config.contains("Range",Tag.TAG_INT),config.getInt("Range"),true);local=TransmissionSettings.readLocal(config.contains("Local",Tag.TAG_BYTE),config.getBoolean("Local"),true);
            mount=ControlMountSettings.read(new ModernHousingTags(tag),0,0,0);var source=com.vandorlabs.persistence.RedstoneData.Source.read(new ModernHousingTags(tag));rotation=source.mountRotation;initialized=source.initialized;reader=next;retained=original;for(String key:READER_FIELDS)retained.remove(key);for(String key:List.of("id","x","y","z"))retained.remove(key);NativePlatformComponents.forgetLoadedFields(retained);inert=false;
        }catch(RuntimeException failure){retained=original;inert=true;reader=newReader();channels=transmission=ChannelList.EMPTY;mount=ControlMountSettings.of(0,0,0);rotation=0;}
        recoveryTaken=false;project();pending=true;if(level instanceof ServerLevel&&!inert)NativeRedstoneChannels.queueRegistration(this);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){tag.merge(retained.copy());if(inert){NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);tag.merge(reader.write(registries));var primitive=new ModernHousingTags(tag);new com.vandorlabs.persistence.RedstoneData.Source(channels.first(),outputLevel()>0,initialized,rotation).write(primitive);tag.putIntArray("RedstoneChannels",channels.toArray());int[] levels=new int[channels.size()];java.util.Arrays.fill(levels,outputLevel());tag.putIntArray("LatchedChannels",outputLevel()>0?channels.toArray():new int[0]);tag.putIntArray("LatchedLevels",levels);ControlMountSettings.write(primitive,mount.height,mount.tilt,mount.direction);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        project();var out=configuration();var v=visual;out.putBoolean("ReaderProjection",true);out.putBoolean("ChipSlot",chip);out.putInt("MountRotation",rotation);out.putInt("AllowedTokens",v.allowed);out.putBoolean("ReaderInert",inert);out.putInt("Company",v.contents.company());out.putInt("Logo",v.contents.logo());out.putBoolean("Icon",v.contents.icon());out.putInt("Output",v.contents.output());out.putBoolean("Occupied",v.contents.occupied());out.putLong("Sequence",v.motion.sequence());out.putInt("Age",v.motion.age());out.putBoolean("Returning",v.motion.returning());out.putInt("Rejected",v.motion.rejectedFor());out.putLong("RejectionDeadline",v.motion.rejectionDeadline());return out;
    }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){
        if(level==null||!level.isClientSide)return;
        applyProjection(tag);
    }
    private void applyProjection(CompoundTag tag){
        try{var nextMount=ControlMountSettings.read(new ModernHousingTags(tag),0,0,0);var contents=new NativeTokenReader.Visual(chip,tag.getInt("Company"),tag.getInt("Logo"),tag.getBoolean("Icon"),tag.getInt("Output"),tag.getBoolean("Occupied"));var motion=new NativeTokenReader.Motion(tag.getLong("Sequence"),tag.getInt("Age"),tag.getBoolean("Returning"),tag.getInt("Rejected"),tag.getLong("RejectionDeadline"));visual=new Visual(nextMount,tag.getInt("MountRotation")&3,tag.getInt("AllowedTokens")&127,contents,motion,tag.getBoolean("ReaderInert"));mount=nextMount;rotation=visual.rotation;}catch(RuntimeException malformed){/* Keep the last coherent public projection. */}
    }
}
