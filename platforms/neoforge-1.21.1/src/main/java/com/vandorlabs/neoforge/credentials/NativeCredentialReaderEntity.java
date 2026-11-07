package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.neoforge.screen.NativeChipDisplay;
import com.vandorlabs.neoforge.screen.NativeScreenChipView;
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

/** Standalone host. Physical ownership never enters its public projection or configured pick. */
public class NativeCredentialReaderEntity extends BlockEntity implements NativeChannelMember,net.minecraft.world.Container {
    private static final List<String> READER_FIELDS=List.of("TokenLogo","TokenRejectedUntil","ChipSlot","AnyToken","AllowedTokens","TokenBehavior","TokenPulseUntil","TokenToggled","TokenToggleApplied","TokenInsertStarted","TokenInsertionSequence","TokenReturnTo","TokenReturnToMost","TokenReturnToLeast","InsertedToken");
    public record Visual(ControlMountSettings mount,int rotation,int allowed,NativeTokenReader.Visual contents,NativeTokenReader.Motion motion,boolean inert,int behavior,NativeScreenChipView chipView){}
    private final boolean chip;
    private NativeTokenReader reader;
    private CompoundTag retained=new CompoundTag();
    private ChannelList channels=ChannelList.EMPTY,transmission=ChannelList.EMPTY;
    private ControlMountSettings mount=ControlMountSettings.of(0,0,0);
    private int rotation,range=16;
    private boolean inert,local=true,pending=true,publishing,recoveryTaken,initialized,projectionOnly,removing;
    private volatile Visual visual;
    private volatile net.neoforged.neoforge.client.model.data.ModelData modelData=net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
    @Override public net.neoforged.neoforge.client.model.data.ModelData getModelData(){return modelData;}
    private void installVisual(Visual next){Visual previous=visual;boolean geometry=previous==null||previous.rotation()!=next.rotation()||previous.mount().height!=next.mount().height||previous.mount().tilt!=next.mount().tilt||previous.mount().direction!=next.mount().direction;if(geometry)modelData=net.neoforged.neoforge.client.model.data.ModelData.builder().with(NativeBinaryControlBlockEntity.VISUAL,new NativeBinaryControlBlockEntity.Visual(next.mount(),next.rotation())).build();visual=next;if(geometry&&level!=null&&level.isClientSide)requestModelDataUpdate();}
    private NativeTokenReader displayReader;
    private long displayRevision=Long.MIN_VALUE;
    private NativeChipDisplay display;
    public NativeCredentialReaderEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,boolean chip){super(type,pos,state);this.chip=chip;reader=newReader();project();}
    private NativeTokenReader newReader(){return new NativeTokenReader(new NativeTokenReader.Host(){
        public BlockEntity owner(){return NativeCredentialReaderEntity.this;}
        public boolean usable(ServerPlayer player){return NativeCredentialReaderEntity.this.usable(player);}
        public void changed(){publish();}
        public void schedule(int ticks){if(level instanceof ServerLevel server&&!inert&&!removing)server.scheduleTick(worldPosition,getBlockState().getBlock(),Math.max(1,ticks));}
        public boolean chip(boolean ignored){return chip;}
    },chip);}
    public boolean chip(){return chip;}
    public boolean inert(){return projectionOnly&&level!=null&&level.isClientSide?visual.inert:inert;}
    public Visual visual(){return visual;}
    /** Bounded immutable display-only data; adjacent Screens never obtain the physical token. */
    public NativeScreenChipView chipView(){return visual.chipView;}
    public ControlMountSettings mountSettings(){return mount;}
    public int mountRotation(){return rotation;}
    public int behavior(){return visual.behavior;}
    public boolean localRedstone(){return local;}
    public boolean usable(ServerPlayer player){return !inert&&!removing&&level instanceof ServerLevel server&&server.getServer().isSameThread()&&NativeRedstoneChannels.open(server)&&NativeConfigurationAccess.canEditDialog(player,this);}
    public int outputLevel(){return inert||removing?0:reader.outputLevel(level==null?0:level.getGameTime());}
    public int physicalLevel(){return local?outputLevel():0;}
    public boolean interact(ServerPlayer player,InteractionHand hand){return usable(player)&&reader.interact(player,hand,false);}
    public boolean holdingCredential(ServerPlayer player,InteractionHand hand){return reader.holdingCredential(player,hand,false);}
    private boolean inventoryActive(){return !inert&&!removing&&level instanceof ServerLevel server&&server.getServer().isSameThread()&&NativeRedstoneChannels.open(server)&&isChannelMemberActive();}
    @Override public int getContainerSize(){return 1;}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean isEmpty(){return !inventoryActive()||reader.inventoryStack().isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0&&inventoryActive()?reader.inventoryStack():ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){return slot==0&&count>0&&inventoryActive()&&!publishing?reader.takeFromInventory():ItemStack.EMPTY;}
    @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,1);}
    @Override public void setItem(int slot,ItemStack stack){if(slot==0&&inventoryActive()&&!publishing)reader.setFromInventory(stack);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0&&inventoryActive()&&reader.behavior()<2&&!reader.returning()&&reader.authorized(stack);}
    @Override public void clearContent(){if(inventoryActive()&&!publishing)reader.takeFromInventory();}
    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player){return player instanceof ServerPlayer server&&usable(server);}
    ItemStack takeForRemoval(){removing=true;return takeForDrop();}
    public ItemStack takeForDrop(){return inert?ItemStack.EMPTY:reader.takeForDrop();}
    public ItemStack recoveryForRemoval(net.minecraft.world.item.Item item){if(!inert||recoveryTaken||retained.isEmpty())return ItemStack.EMPTY;recoveryTaken=true;var result=new ItemStack(item);var tag=new CompoundTag();tag.put("NativeReaderRecovery",retained.copy());result.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));return result;}
    /** Bounded main-thread copied choices; credentials and return ownership never enter this value. */
    public com.vandorlabs.neoforge.copifier.NativeCopifierReaderEdit.Settings copiedSettings(){if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Reader copy requires owning thread");return new com.vandorlabs.neoforge.copifier.NativeCopifierReaderEdit.Settings(mount,rotation,channels,transmission,range,local,outputLevel()>0);}
    /** The outer copied-target/session layer authorizes the actor; no editor-distance check is added here. */
    public boolean configureCopied(CompoundTag values){
        if(publishing||inert||removing||!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!NativeRedstoneChannels.open(server)||!isChannelMemberActive())return false;
        com.vandorlabs.neoforge.copifier.NativeCopifierReaderEdit.Plan plan;try{plan=com.vandorlabs.neoforge.copifier.NativeCopifierReaderEdit.prepare(copiedSettings(),values);}catch(IllegalArgumentException refused){return false;}if(!plan.applicable())return false;var next=plan.settings();
        CompoundTag nextRetained=retained;if(values.contains("Transmission",Tag.TAG_COMPOUND)){nextRetained=retained.copy();var forge=nextRetained.getCompound("ForgeData").copy();var all=forge.getCompound(TransmissionSettings.SETTINGS).copy();var main=all.getCompound("main").copy();main.putIntArray("Channels",next.outputs().toArray());main.putInt("Range",next.range());main.putBoolean("Local",next.local());all.put("main",main);forge.put(TransmissionSettings.SETTINGS,all);nextRetained.put("ForgeData",forge);}var staged=nextRetained;
        NativeRedstoneChannels.batch(server,()->{retained=staged;mount=next.mount();rotation=next.rotation();channels=next.channels();transmission=next.outputs();range=next.range();local=next.local();publish();NativeRedstoneChannels.channelsChanged(this);});return true;
    }
    public boolean configure(ServerPlayer player,boolean any,int allowed,int behavior,ControlMountSettings next){
        if(publishing||!usable(player)||next==null||!ControlMountSettings.valid(next.height,next.tilt,next.direction)||behavior<0||behavior>3)return false;
        boolean changed=reader.configureLocal(any,allowed,behavior)||mount.height!=next.height||mount.tilt!=next.tilt||mount.direction!=next.direction||!channels.equals(transmission);
        mount=next;channels=transmission;if(changed){publish();NativeRedstoneChannels.channelsChanged(this);}return true;
    }
    public CompoundTag configuration(){var out=new CompoundTag();out.putInt("Channel",channels.first());out.putIntArray("RedstoneChannels",channels.toArray());ControlMountSettings.write(new ModernHousingTags(out),mount.height,mount.tilt,mount.direction);return out;}
    /** Vanilla Ctrl-pick must retain the same inventory-free choices as ordinary configured pick. */
    @Override public void saveToItem(ItemStack stack,HolderLookup.Provider registries){var out=new CompoundTag();out.put("RedstoneChannelSettings",configuration());stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(out));stack.remove(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);}
    public void place(CompoundTag choices,int ownRotation){if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!NativeRedstoneChannels.open(server)||!isChannelMemberActive())return;channels=readChannels(choices);transmission=channels;rotation=ownRotation&3;mount=ControlMountSettings.read(new ModernHousingTags(choices),0,0,0);publish();NativeRedstoneChannels.channelsChanged(this);}
    public boolean configureTransmission(ServerPlayer player,ChannelList next,int nextRange,boolean nextLocal){
        if(publishing||!usable(player)||!TransmissionSettings.validConfiguration(next,nextRange))return false;
        var forge=retained.getCompound("ForgeData").copy();var settings=forge.getCompound(TransmissionSettings.SETTINGS).copy();var main=settings.getCompound("main").copy();main.putIntArray("Channels",next.toArray());main.putInt("Range",nextRange);main.putBoolean("Local",nextLocal);settings.put("main",main);forge.put(TransmissionSettings.SETTINGS,settings);retained.put("ForgeData",forge);
        channels=transmission=next;range=nextRange;local=nextLocal;publish();NativeRedstoneChannels.channelsChanged(this);return true;
    }
    public void scheduledTick(){if(!inert)reader.scheduledTick();}
    private void project(){long now=level==null?0:level.getGameTime();var contents=reader.visual(now);var motion=reader.motion(now);NativeScreenChipView view=null;
        if(chip&&!inert){long revision=reader.contentsRevision();if(displayReader!=reader||displayRevision!=revision){display=NativeChipDisplay.prepare(reader.tokenCopy());displayReader=reader;displayRevision=revision;}view=NativeScreenChipView.of(display,contents,motion,0);}else {display=null;displayReader=null;displayRevision=Long.MIN_VALUE;}installVisual(new Visual(mount,rotation,reader.effectiveAllowed(),contents,motion,inert,reader.behavior(),view));
    }
    private void publish(){
        if(publishing){project();return;}publishing=true;try{
            initialized=true;project();setChanged();project();NativeCredentialReaderViews.changed(this);if(!(level instanceof ServerLevel server)||!isChannelMemberActive())return;
            BlockState state=getBlockState();boolean on=outputLevel()>0;if(state.hasProperty(NativeMountedSwitchBlock.ON)&&state.getValue(NativeMountedSwitchBlock.ON)!=on)server.setBlock(worldPosition,state.setValue(NativeMountedSwitchBlock.ON,on),2);
            server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);NativeRedstoneChannels.outputChanged(this);
            for(Direction face:Direction.values()){BlockPos neighbor=worldPosition.relative(face);if(!server.hasChunkAt(neighbor))continue;server.neighborChanged(neighbor,getBlockState().getBlock(),worldPosition);if(server.getBlockState(neighbor).isRedstoneConductor(server,neighbor))for(Direction side:Direction.values()){BlockPos target=neighbor.relative(side);if(server.hasChunkAt(target))server.neighborChanged(target,getBlockState().getBlock(),neighbor);}}
        }finally{publishing=false;}
    }
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public boolean isChannelMemberActive(){return !removing&&!inert&&NativeChannelMember.super.isChannelMemberActive();}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public ChannelList transmittedChannels(){return transmission;}
    @Override public int transmissionRange(){return range;}
    @Override public int localSignalLevel(int channel){return outputLevel();}
    @Override public void setChannelLevel(int ignored){} // A received signal cannot become a local reader output.
    @Override public void beforeChannelRegistration(){if(!pending||!isChannelMemberActive())return;pending=false;reader.resume(true);if(isChannelMemberActive())publish();}
    @Override public void onLoad(){super.onLoad();pending=true;NativeCredentialReaderViews.changed(this);if(level instanceof ServerLevel)NativeRedstoneChannels.queueRegistration(this);}
    @Override public void setLevel(net.minecraft.world.level.Level level){super.setLevel(level);if(projectionOnly&&!level.isClientSide){project();NativeCredentialReaderViews.changed(this);}}
    @Override public void setRemoved(){NativeRedstoneChannels.unregister(this);super.setRemoved();NativeCredentialReaderViews.changed(this);}
    @Override public void onChunkUnloaded(){NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();NativeCredentialReaderViews.changed(this);}
    private static ChannelList readChannels(CompoundTag tag){return readChannels(tag,tag.getInt("Channel"));}
    private static ChannelList readChannels(CompoundTag tag,int legacy){ChannelList fallback=ChannelList.of(Math.max(0,legacy));if(!tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))return fallback;try{return ChannelList.of(tag.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException invalid){return fallback;}}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        CompoundTag original=tag.copy();if(tag.getBoolean("ReaderProjection")){retained=original;inert=true;projectionOnly=true;reader=newReader();applyProjection(tag);if(level instanceof ServerLevel)project();return;}projectionOnly=false;try(var guard=NativePlatformComponents.guardLoad(this)){
            if(tag.contains("NativeReaderRecovery"))throw new IllegalArgumentException("Reader recovery ownership");if(original.contains("ForgeCaps")&&(!(original.get("ForgeCaps") instanceof CompoundTag caps)||!caps.isEmpty()))throw new IllegalArgumentException("Unsupported legacy reader capability ownership");CompoundTag prepared=original.copy();
            if(prepared.getCompound("ForgeData").contains("VandorTransmitters")){if(!prepared.getCompound("ForgeData").contains("VandorTransmitters",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Unknown reader module envelope");var migration=NativeTransmissionMigration.read(prepared);if(!migration.installable())throw new IllegalArgumentException("Unknown reader development module ownership");prepared=migration.install();}
            NativePlatformComponents.requireSupported(prepared,registries);super.loadAdditional(prepared,registries);guard.requireSuccessful();
            NativeTokenReader next=newReader();CompoundTag data=new CompoundTag();for(String key:READER_FIELDS)if(prepared.contains(key))data.put(key,prepared.get(key).copy());next.read(data,registries);if(next.opaqueOccupied())throw new IllegalArgumentException("Unresolved physical reader owner");var nextDisplay=chip?NativeChipDisplay.prepare(next.tokenCopy()):null;
            var primitive=new ModernHousingTags(prepared);var source=com.vandorlabs.persistence.RedstoneData.Source.read(primitive);channels=readChannels(prepared,source.channel);var config=prepared.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");transmission=TransmissionSettings.readChannels(config.contains("Channels",Tag.TAG_INT_ARRAY),config.getIntArray("Channels"),channels);range=TransmissionSettings.readRange(config.contains("Range",Tag.TAG_INT),config.getInt("Range"),true);local=TransmissionSettings.readLocal(config.contains("Local",Tag.TAG_BYTE),config.getBoolean("Local"),true);
            mount=ControlMountSettings.read(primitive,0,0,0);rotation=source.mountRotation;initialized=source.initialized;reader=next;display=nextDisplay;displayReader=next;displayRevision=next.contentsRevision();retained=prepared;for(String key:READER_FIELDS)retained.remove(key);for(String key:List.of("id","x","y","z"))retained.remove(key);NativePlatformComponents.forgetLoadedFields(retained);inert=false;
        }catch(RuntimeException failure){retained=original;inert=true;reader=newReader();channels=transmission=ChannelList.EMPTY;mount=ControlMountSettings.of(0,0,0);rotation=0;}
        recoveryTaken=false;project();NativeCredentialReaderViews.changed(this);pending=true;if(level instanceof ServerLevel&&!inert)NativeRedstoneChannels.queueRegistration(this);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){tag.merge(retained.copy());if(inert){NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);tag.merge(reader.write(registries));var primitive=new ModernHousingTags(tag);new com.vandorlabs.persistence.RedstoneData.Source(channels.first(),outputLevel()>0,initialized,rotation).write(primitive);tag.putIntArray("RedstoneChannels",channels.toArray());int[] levels=new int[channels.size()];java.util.Arrays.fill(levels,outputLevel());tag.putIntArray("LatchedChannels",outputLevel()>0?channels.toArray():new int[0]);tag.putIntArray("LatchedLevels",levels);ControlMountSettings.write(primitive,mount.height,mount.tilt,mount.direction);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        project();var out=configuration();var v=visual;out.putBoolean("ReaderProjection",true);out.putBoolean("ChipSlot",chip);out.putInt("MountRotation",rotation);out.putInt("AllowedTokens",v.allowed);out.putInt("TokenBehavior",v.behavior);out.putIntArray("TransmitChannels",transmission.toArray());out.putInt("TransmitRange",range);out.putBoolean("LocalRedstone",local);out.putBoolean("ReaderInert",inert);out.putInt("Company",v.contents.company());out.putInt("Logo",v.contents.logo());out.putBoolean("Icon",v.contents.icon());out.putInt("Output",v.contents.output());out.putBoolean("Occupied",v.contents.occupied());out.putLong("Sequence",v.motion.sequence());out.putInt("Age",v.motion.age());out.putBoolean("Returning",v.motion.returning());out.putInt("Rejected",v.motion.rejectedFor());out.putLong("RejectionDeadline",v.motion.rejectionDeadline());if(v.chipView!=null)out.put("ReaderChipView",v.chipView.write());return out;
    }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){
        if(level==null||!level.isClientSide)return;
        applyProjection(tag);
    }
    private void applyProjection(CompoundTag tag){
        boolean installed=false;try{var nextMount=ControlMountSettings.read(new ModernHousingTags(tag),0,0,0);var contents=new NativeTokenReader.Visual(chip,tag.getInt("Company"),tag.getInt("Logo"),tag.getBoolean("Icon"),tag.getInt("Output"),tag.getBoolean("Occupied"));var motion=new NativeTokenReader.Motion(tag.getLong("Sequence"),tag.getInt("Age"),tag.getBoolean("Returning"),tag.getInt("Rejected"),tag.getLong("RejectionDeadline"));int behavior=tag.getInt("TokenBehavior");if(behavior<0||behavior>3)throw new IllegalArgumentException("Reader behavior projection");var nextChannels=readChannels(tag);var nextTransmission=ChannelList.of(tag.getIntArray("TransmitChannels"));int nextRange=tag.getInt("TransmitRange");if(!TransmissionSettings.validRange(nextRange))throw new IllegalArgumentException("Reader range projection");NativeScreenChipView nextView=null;if(tag.contains("ReaderChipView")){if(!chip||tag.getBoolean("ReaderInert")||!tag.contains("ReaderChipView",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Reader chip view envelope");nextView=NativeScreenChipView.read(tag.getCompound("ReaderChipView"));if(!nextView.reader.equals(contents)||nextView.motion.sequence()!=motion.sequence()||nextView.motion.age()!=motion.age()||nextView.motion.rejectedFor()!=motion.rejectedFor()||nextView.motion.rejectionDeadline()!=motion.rejectionDeadline())throw new IllegalArgumentException("Incoherent reader chip view");}mount=nextMount;rotation=tag.getInt("MountRotation")&3;channels=nextChannels;transmission=nextTransmission;range=nextRange;local=tag.getBoolean("LocalRedstone");inert=true;projectionOnly=true;installVisual(new Visual(nextMount,rotation,tag.getInt("AllowedTokens")&127,contents,motion,tag.getBoolean("ReaderInert"),behavior,nextView));installed=true;}catch(RuntimeException malformed){/* Keep the last coherent public projection. */}if(installed)NativeCredentialReaderViews.changed(this);
    }
}
