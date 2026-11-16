package com.vandorlabs.neoforge.rack;

import com.vandorlabs.rack.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.redstone.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Four physical internal modules and event-driven channels; no tile ticker or automation inventory. */
public final class NativeRackEntity extends BlockEntity {
    public record Visual(int occupied,int kinds,int visible,int housing,boolean inert,int batteryLevels) { public Visual(int occupied,int kinds,int visible,int housing,boolean inert){this(occupied,kinds,visible,housing,inert,0);} }
    public interface ClientUpdates {void updated(NativeRackEntity tile,int previousMask,int previousKinds,long[] sequences,boolean initial);}
    public static ClientUpdates CLIENT_UPDATES=(tile,mask,kinds,sequences,initial)->{};
    public static final ModelProperty<Visual> VISUAL=new ModelProperty<>();
    private static final AtomicLong EPOCHS=new AtomicLong();
    private final long epoch=EPOCHS.incrementAndGet();
    private long receivedEpoch,revision;
    private final ItemStack[] items={ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
    private final long[] inserted=new long[4];
    private final AutopilotModuleSettings[] publicModules=new AutopilotModuleSettings[4];
    private final Receiver[] receivers={new Receiver(0),new Receiver(1),new Receiver(2),new Receiver(3)};
    public final Output output=new Output();
    private NativeRackData.Transmission transmission=NativeRackData.Transmission.fresh();
    private int housing=-1,occupied,kinds,visible,physical;
    private int publicBatteryLevels;
    private boolean emit,inert,physicalReady,forcedRecovery,transferred;
    private CompoundTag retained=new CompoundTag();
    private volatile Visual visual;
    private volatile ModelData model;
    public long serialized,visualPackets;
    public NativeRackEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);publish();}
    public Visual visual(){return visual;}
    @Override public ModelData getModelData(){return model;}
    public boolean inert(){return inert;}
    public void forceRecovery(boolean value){forcedRecovery=value;}
    public boolean takeRecovery(){boolean result=forcedRecovery;forcedRecovery=false;return result;}
    public int housing(){return housing;}
    public boolean emits(){return emit;}
    public int occupied(){return occupied;}
    public int kinds(){return kinds;}
    public int visible(){return visible;}
    public int kind(int slot){return ModuleRackPolicy.slotKind(kinds,slot);}
    public long sequence(int slot){return ModuleRackGeometry.validSlot(slot)?inserted[slot]:0;}
    public long incarnation(){return receivedEpoch>0?receivedEpoch:epoch;}
    public NativeRackData.Transmission transmission(){return transmission;}
    public AutopilotModuleSettings moduleSettings(int slot){if(kind(slot)!=2)throw new IllegalArgumentException("No Autopilot module");return level!=null&&level.isClientSide?publicModules[slot]:NativeRackData.settings(items[slot]);}
    public ItemStack module(int slot){return ModuleRackGeometry.validSlot(slot)?items[slot].copy():ItemStack.EMPTY;}
    public List<ItemStack> ownedModuleDrops(){var result=new ArrayList<ItemStack>();if(!inert)for(var stack:items)if(!stack.isEmpty())result.add(stack.copy());return List.copyOf(result);}
    private int previousBatteryPixels;
    public int batteryPixels(int slot){return com.vandorlabs.rack.BatteryChargeBar.unpack(level!=null&&level.isClientSide?publicBatteryLevels:batteryLevels(),slot);}
    public int previousBatteryPixels(int slot){return com.vandorlabs.rack.BatteryChargeBar.unpack(previousBatteryPixels,slot);}
    public int batteryLevels(){int result=0;for(int slot=0;slot<4;slot++)result|=com.vandorlabs.rack.BatteryChargeBar.pixels(NativeBatteryModule.energy(items[slot]),NativeBatteryModule.CAPACITY)<<(slot*6);return result;}
    public int batteryEnergy(){int total=0;for(var stack:items)total+=NativeBatteryModule.energy(stack);return total;}
    public int transferBattery(int maximum,boolean simulate,boolean receive){if(!installed()||inert)return 0;int before=batteryLevels(),total=0;for(var stack:items)total+=NativeBatteryModule.transfer(stack,Math.max(0,maximum)-total,simulate,receive);if(!simulate&&total>0){setChanged();if(before!=batteryLevels())changed();}return total;}
    private boolean installed(){
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!NativeRedstoneChannels.open(server)||isRemoved()||held())return false;
        var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
        return chunk!=null&&chunk.getBlockEntities().get(worldPosition)==this;
    }
    private boolean held(){return com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,worldPosition);}
    private static long next(long current){return current==Long.MAX_VALUE?1:current+1;}
    private void derive(){occupied=kinds=0;for(int slot=0;slot<4;slot++)if(!items[slot].isEmpty()){occupied|=1<<slot;kinds|=NativeRackData.kind(items[slot])<<(slot*2);publicModules[slot]=kind(slot)==2?NativeRackData.settings(items[slot]):null;}visible=occupied;}
    private void publish(){visual=new Visual(occupied,kinds,visible,housing,inert,level!=null&&level.isClientSide?publicBatteryLevels:batteryLevels());model=ModelData.builder().with(VISUAL,visual).build();if(level!=null&&level.isClientSide&&level.getBlockEntity(worldPosition)==this)requestModelDataUpdate();}
    private void changed(){revision=next(revision);setChanged();publish();visualPackets++;if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    public boolean insert(int slot,ItemStack held,boolean creative){
        if(!installed()||inert||!ModuleRackGeometry.validSlot(slot)||!items[slot].isEmpty()||NativeRackData.kind(held)==0||NativeRackData.kind(held)==3&&!NativeBatteryModule.valid(held)||com.vandorlabs.neoforge.inventory.NativeInventoryItems.containedOwnership(held))return false;
        var owned=held.copyWithCount(1);if(NativeRackData.kind(owned)==2){NativeRackData.settings(owned);NativeRackData.placed(owned);}
        unregisterReceivers();items[slot]=owned;if(!creative)held.shrink(1);inserted[slot]=next(inserted[slot]);derive();registerReceivers();changed();outputChanged();return true;
    }
    public ItemStack extract(int slot){
        if(!installed()||inert||!ModuleRackGeometry.validSlot(slot)||items[slot].isEmpty())return ItemStack.EMPTY;
        unregisterReceivers();ItemStack owned=items[slot];items[slot]=ItemStack.EMPTY;derive();registerReceivers();changed();outputChanged();return owned;
    }
    /** Called only by a durable native ownership installer, after it has frozen the complete original snapshot. */
    public void clearForTransfer(){if(!installed()||inert)throw new IllegalStateException("Rack transfer owner changed");unregisterReceivers();Arrays.fill(items,ItemStack.EMPTY);derive();transferred=true;setChanged();}
    public boolean transferred(){return transferred;}
    /** Native removal hooks publish the resulting drops; clear internal ownership before callbacks. */
    public List<ItemStack> drain(){if(!installed()||inert)return List.of();unregisterReceivers();var result=ownedModuleDrops();Arrays.fill(items,ItemStack.EMPTY);derive();setChanged();outputChanged();return result;}
    public boolean configure(int casing,boolean output,NativeRackData.Transmission bank){
        if(!installed()||inert||bank==null||casing< -1||casing>=0&&!ModernFinishCatalog.validChoice(casing))return false;
        if(casing==housing&&output==emit&&bank.equals(transmission))return true;
        housing=casing;emit=output;transmission=bank;changed();NativeRedstoneChannels.channelsChanged(this.output);outputChanged();return true;
    }
    public boolean configureModule(int slot,long sequence,AutopilotModuleSettings settings){
        if(!installed()||inert||settings==null||kind(slot)!=2||inserted[slot]!=sequence)return false;
        unregisterReceivers();NativeRackData.configure(items[slot],settings);derive();registerReceivers();changed();return true;
    }
    public void renderMask(int mask){mask&=occupied;if(visible==mask)return;visible=mask;publish();if(level!=null&&level.isClientSide&&level.getBlockEntity(worldPosition)==this)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    private void outputChanged(){if(level instanceof ServerLevel server){NativeRedstoneChannels.outputChanged(output);if(transmission.local())server.updateNeighborsAt(worldPosition,getBlockState().getBlock());}}
    private void unregisterReceivers(){if(level instanceof ServerLevel)for(var receiver:receivers)NativeRedstoneChannels.unregister(receiver);}
    private void registerReceivers(){if(level instanceof ServerLevel&&!inert)for(int slot=0;slot<4;slot++)if(kind(slot)==2)NativeRedstoneChannels.queueRegistration(receivers[slot]);}
    private void ensurePhysical(){if(!physicalReady&&level instanceof ServerLevel server){physical=NativeLoadedRedstonePower.level(server,worldPosition);physicalReady=true;}}
    public void neighborChanged(){if(!installed()||inert)return;physicalReady=false;ensurePhysical();for(int slot=0;slot<4;slot++)if(kind(slot)==2)receivers[slot].setChannelLevel(receivers[slot].wireless);}
    @Override public void onLoad(){super.onLoad();physicalReady=false;if(level instanceof ServerLevel){NativeRedstoneChannels.queueRegistration(output);registerReceivers();}}
    private void unload(){unregisterReceivers();if(level instanceof ServerLevel)NativeRedstoneChannels.unregister(output);}
    @Override public void setRemoved(){unload();super.setRemoved();}
    @Override public void onChunkUnloaded(){unload();super.onChunkUnloaded();}
    public final class Output implements NativeChannelMember {
        public BlockEntity channelBlockEntity(){return NativeRackEntity.this;}
        public ChannelList getRedstoneChannels(){return inert?ChannelList.EMPTY:transmission.channels();}
        public ChannelList transmittedChannels(){return getRedstoneChannels();}
        public int transmissionRange(){return inert?-1:transmission.range();}
        public int localSignalLevel(int ignored){return inert||!emit?0:ModuleRackPolicy.signalLevel(occupied);}
        public void setChannelLevel(int ignored){}
        public void beforeChannelRegistration(){ensurePhysical();}
    }
    private final class Receiver implements NativeChannelMember {
        final int slot;int wireless;
        Receiver(int slot){this.slot=slot;}
        public BlockEntity channelBlockEntity(){return NativeRackEntity.this;}
        public ChannelList getRedstoneChannels(){return inert||kind(slot)!=2?ChannelList.EMPTY:moduleSettings(slot).channels;}
        public void beforeChannelRegistration(){ensurePhysical();}
        public void setChannelLevel(int value){wireless=Math.max(0,Math.min(15,value));if(kind(slot)==2&&!inert&&NativeRackData.signal(items[slot],physical>0||wireless>0))setChanged();}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        serialized++;tag.merge(retained.copy());if(inert){NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);
        tag.putInt("NativeRackSchema",1);var modules=new ListTag();for(int slot=0;slot<4;slot++)if(!items[slot].isEmpty())modules.add(NativeRackData.writeItem(items[slot],slot,registries));tag.put("Items",modules);
        if(housing>=0)tag.putInt("RackHousing",housing);else tag.remove("RackHousing");if(emit)tag.putBoolean("RackEmit",true);else tag.remove("RackEmit");
        NativeRackData.transmission(tag,transmission);visualData(tag);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        if(level!=null&&level.isClientSide&&tag.getBoolean("RackPublic")){readVisual(tag,true);return;}
        CompoundTag original=tag.copy();try{
            NativePlatformComponents.requireSupported(original,registries);
            if(tag.contains("ForgeCaps")&&(!tag.contains("ForgeCaps",Tag.TAG_COMPOUND)||!tag.getCompound("ForgeCaps").isEmpty()))throw new IllegalArgumentException("Unsupported legacy Rack capabilities");
            if(tag.contains("NativeRackSchema")&&(!tag.contains("NativeRackSchema",Tag.TAG_INT)||tag.getInt("NativeRackSchema")!=1))throw new IllegalArgumentException("Future Rack schema");
            if(tag.contains("RackHousing")&&!tag.contains("RackHousing",Tag.TAG_INT)||tag.contains("RackEmit")&&(!tag.contains("RackEmit",Tag.TAG_BYTE)||tag.getByte("RackEmit")<0||tag.getByte("RackEmit")>1))throw new IllegalArgumentException("Rack settings types");
            for(String key:List.of("Revision","Insert0","Insert1","Insert2","Insert3"))if(tag.contains(key)&&(!tag.contains(key,Tag.TAG_LONG)||tag.getLong(key)<0))throw new IllegalArgumentException("Rack sequence type");
            if(tag.contains("Items")&&(!(tag.get("Items") instanceof ListTag modules)||!modules.isEmpty()&&modules.getElementType()!=Tag.TAG_COMPOUND||modules.size()>4))throw new IllegalArgumentException("Rack module list");
            ItemStack[] staged={ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};boolean[] seen=new boolean[4];
            for(Tag value:tag.getList("Items",Tag.TAG_COMPOUND)){var item=(CompoundTag)value;if(!item.contains("Slot",Tag.TAG_BYTE))throw new IllegalArgumentException("Rack module slot type");int slot=item.getByte("Slot")&255;if(slot>=4||seen[slot])throw new IllegalArgumentException("Rack module slot");seen[slot]=true;staged[slot]=NativeRackData.readItem(item,registries);}
            int casing=tag.contains("RackHousing")?tag.getInt("RackHousing"):-1;
            if(casing< -1||casing>=0&&!ModernFinishCatalog.validChoice(casing))throw new IllegalArgumentException("Rack housing");
            var bank=NativeRackData.transmission(tag);
            try(var guard=NativePlatformComponents.guardLoad(this)){super.loadAdditional(original,registries);guard.requireSuccessful();}
            System.arraycopy(staged,0,items,0,4);housing=casing;emit=tag.getBoolean("RackEmit");transmission=bank;revision=Math.max(0,tag.getLong("Revision"));for(int slot=0;slot<4;slot++)inserted[slot]=Math.max(0,tag.getLong("Insert"+slot));
            retained=original;for(String key:List.of("Items","RackHousing","RackEmit","ModuleMask","ModuleKinds","Revision","Insert0","Insert1","Insert2","Insert3","NativeRackSchema"))retained.remove(key);NativePlatformComponents.forgetLoadedFields(retained);inert=false;derive();
        }catch(RuntimeException failure){retained=original;inert=true;Arrays.fill(items,ItemStack.EMPTY);occupied=kinds=visible=0;}physicalReady=false;publish();
    }
    private void visualData(CompoundTag tag){tag.putInt("ModuleMask",occupied);if(kinds!=ModuleRackPolicy.blankKinds(occupied))tag.putInt("ModuleKinds",kinds);else tag.remove("ModuleKinds");for(int slot=0;slot<4;slot++)if(kind(slot)==3)tag.putInt("BatteryCharge"+slot,NativeBatteryModule.energy(items[slot]));tag.putLong("Revision",revision);for(int slot=0;slot<4;slot++)tag.putLong("Insert"+slot,inserted[slot]);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        var tag=new CompoundTag();visualData(tag);tag.putBoolean("RackPublic",true);tag.putLong("RackEpoch",epoch);tag.putInt("RackHousing",housing);tag.putBoolean("RackEmit",emit);tag.putBoolean("NativeRackInert",inert);NativeRackData.transmission(tag,transmission);
        for(int slot=0;slot<4;slot++)if(kind(slot)==2)tag.put("ModuleConfig"+slot,NativeRackData.publicSettings(moduleSettings(slot)));return tag;
    }
    private void readVisual(CompoundTag tag,boolean initial){
        long incomingEpoch=Math.max(0,tag.getLong("RackEpoch")),incoming=Math.max(0,tag.getLong("Revision"));
        if(incomingEpoch>0&&receivedEpoch>0&&incomingEpoch<receivedEpoch)return;
        boolean replacement=incomingEpoch>0&&incomingEpoch!=receivedEpoch;if(!replacement&&receivedEpoch>0&&incoming<revision&&!(revision==Long.MAX_VALUE&&incoming==1))return;
        if(!replacement&&receivedEpoch>0&&incoming==revision)return;
        int previous=replacement?0:occupied,previousKinds=replacement?0:kinds;long[] before=replacement?new long[4]:inserted.clone();
        previousBatteryPixels=publicBatteryLevels;
        occupied=tag.getInt("ModuleMask")&15;kinds=tag.contains("ModuleKinds")?tag.getInt("ModuleKinds")&255:ModuleRackPolicy.blankKinds(occupied);visible=initial?occupied:visible&occupied;housing=tag.getInt("RackHousing");emit=tag.getBoolean("RackEmit");inert=tag.getBoolean("NativeRackInert");transmission=NativeRackData.transmission(tag);revision=incoming;if(incomingEpoch>0)receivedEpoch=incomingEpoch;
        publicBatteryLevels=0;for(int slot=0;slot<4;slot++)if(kind(slot)==3)publicBatteryLevels|=com.vandorlabs.rack.BatteryChargeBar.pixels(tag.getInt("BatteryCharge"+slot),NativeBatteryModule.CAPACITY)<<(slot*6);
        for(int slot=0;slot<4;slot++){inserted[slot]=Math.max(0,tag.getLong("Insert"+slot));publicModules[slot]=kind(slot)==2?NativeRackData.settings(tag.getCompound("ModuleConfig"+slot)):null;}
        publish();CLIENT_UPDATES.updated(this,previous,previousKinds,before,initial);if(level!=null&&level.isClientSide&&level.getBlockEntity(worldPosition)==this)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){readVisual(tag,true);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){readVisual(packet.getTag(),false);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
