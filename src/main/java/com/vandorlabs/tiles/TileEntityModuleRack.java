package com.vandorlabs.tiles;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.rack.ModuleRackGeometry;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.concurrent.atomic.AtomicLong;

/** Four physical modules; no server/client tick registration and no idle packets.
 * Saved item payloads never enter appearance packets. Cosmetic motion is owned
 * by the client active-animation collection, not by a per-rack renderer.
 */
public final class TileEntityModuleRack extends TileEntityAutopilot implements net.minecraft.util.ITickable {
    private static final AtomicLong NEXT_EPOCH = new AtomicLong();
    private static final int MAX_NAVIGATION_LABEL = 256;
    // A new physical server tile owns a new appearance epoch, even when its
    // saved revision comes from an older copied/restored snapshot. Keep this
    // out of saved NBT so exact structure comparisons and transfers stay stable.
    private final long localEpoch = NEXT_EPOCH.incrementAndGet();
    private long receivedEpoch;
    private final ItemStack[] modules = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
    private final long[] insertionSequences = new long[ModuleRackGeometry.SLOTS];
    private volatile int occupiedMask;
    private volatile int renderMask;
    private long revision;
    private int primary = -1;
    private int moduleKinds, previousModuleKinds;
    private boolean loadingModules;
    private NBTTagCompound failedLoad;
    public boolean inert() { return failedLoad != null; }
    private com.vandorlabs.rack.LegacyAutopilotData legacyBlockData;
    private NBTTagCompound legacyOriginal;
    private final ModuleReceiver[] receivers = { new ModuleReceiver(0), new ModuleReceiver(1), new ModuleReceiver(2), new ModuleReceiver(3) };
    private int housingTexture = -1;
    private boolean emitRedstone;
    private boolean powerOut;
    @Override public void update(){
        if(!powerOut||!livePower()||batteryEnergy()==0)return;
        net.minecraft.util.EnumFacing rear=world.getBlockState(pos).getValue(com.vandorlabs.blocks.BlockModuleRack.FACING).getOpposite();
        BlockPos at=pos.offset(rear);
        if(!world.isBlockLoaded(at,false))return;
        TileEntity neighbor=world.getTileEntity(at);
        net.minecraftforge.energy.IEnergyStorage target=neighbor==null?null:neighbor.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,rear.getOpposite());
        if(target==null||!target.canReceive())return;
        int offered=extractBatteryEnergy(1000,true),accepted=target.receiveEnergy(offered,true);
        if(accepted>0)extractBatteryEnergy(target.receiveEnergy(Math.min(offered,accepted),false),false);
    }
    public boolean powerOut(){return powerOut;}
    public void setPowerOut(boolean value){if(powerOut!=value){powerOut=value;configurationChanged();}}
    private boolean livePower(){return world!=null&&canMutate()&&(world.getMinecraftServer()==null||world.getMinecraftServer().isCallingFromMinecraftThread())&&world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockModuleRack&&!com.vandorlabs.gear.ArmTransactions.locked(world,pos)&&!com.vandorlabs.copier.CopyTransactions.locked(world,pos);}
    private boolean powerAvailable(net.minecraft.util.EnumFacing side){
        return livePower()&&side==getWorld().getBlockState(pos).getValue(com.vandorlabs.blocks.BlockModuleRack.FACING).getOpposite();
    }
    private final net.minecraftforge.energy.IEnergyStorage batteryPort=new net.minecraftforge.energy.IEnergyStorage(){
        public int receiveEnergy(int amount,boolean simulate){return !livePower()||powerOut?0:receiveBatteryEnergy(amount,simulate);}
        public int extractEnergy(int amount,boolean simulate){return livePower()&&powerOut?extractBatteryEnergy(amount,simulate):0;}
        public int getEnergyStored(){return batteryEnergy();}
        public int getMaxEnergyStored(){return batteryCapacity();}
        public boolean canReceive(){return livePower()&&!powerOut;}
        public boolean canExtract(){return livePower()&&powerOut;}
    };

    public final com.vandorlabs.rack.ModuleRackOutput output = new com.vandorlabs.rack.ModuleRackOutput(this);
    public int getHousingTexture() { return housingTexture; }
    public boolean emitsRedstone() { return emitRedstone; }
    public void setHousingTexture(int choice) {
        if (choice < -1 || choice >= 0 && !ScreenHousingTextures.validChoice(choice))
            throw new IllegalArgumentException("Invalid rack housing");
        if (housingTexture != choice) { housingTexture = choice; configurationChanged(); }
    }
    public void setEmitsRedstone(boolean value) {
        if (emitRedstone != value) { emitRedstone = value; configurationChanged(); }
    }
    public void configurationChanged() { changed(); }


    public int getOccupiedMask() { return occupiedMask; }
    public int getRenderMask() { return renderMask; }
    public long getRevision() { return revision; }
    /** Stable within one authoritative tile instance; not a saved item identity. */
    public long getIncarnation() { return receivedEpoch > 0 ? receivedEpoch : localEpoch; }
    public long getInsertionSequence(int slot) {
        return ModuleRackGeometry.validSlot(slot) ? insertionSequences[slot] : 0;
    }

    /** Callers receive a copy, so display or drop previews cannot mutate stored ownership. */
    public ItemStack getStackInSlot(int slot) {
        return ModuleRackGeometry.validSlot(slot) ? modules[slot].copy() : ItemStack.EMPTY;
    }

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == ModItems.BLANK_MODULE || stack.getItem() == ModItems.SPEED_MODULE || stack.getItem() == ModItems.AUTOPILOT_MODULE || com.vandorlabs.rack.ModuleBatteryData.valid(stack));
    }

    /** Offline copies may be edited by validation/import code; live clients and carried
     * read-only views cannot acquire or release physical inventory through this API.
     */
    private boolean canMutate() {
        return !inert() && (world == null || !(world instanceof VehicleWorld) && !world.isRemote
                && !isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this);
    }

    public boolean insertModule(int slot, ItemStack held, boolean creative) {
        if (!canMutate() || !ModuleRackGeometry.validSlot(slot)
                || !modules[slot].isEmpty() || !accepts(held)) return false;
        suspendReceivers();
        saveNavigation();
        ItemStack inserted = held.copy();
        inserted.setCount(1);
        if(world!=null && inserted.getItem()==ModItems.AUTOPILOT_MODULE)com.vandorlabs.rack.ModuleAutopilotData.placed(inserted);
        modules[slot] = inserted;
        if (!creative) held.shrink(1);
        occupiedMask |= 1 << slot;
        insertionSequences[slot] = next(insertionSequences[slot]);
        loadNavigation();
        resumeReceivers();
        changed();
        return true;
    }

    public ItemStack removeModule(int slot) {
        if (!canMutate() || !ModuleRackGeometry.validSlot(slot)) return ItemStack.EMPTY;
        ItemStack removed = modules[slot];
        if (removed.isEmpty()) return ItemStack.EMPTY;
        suspendReceivers();
        saveNavigation();
        modules[slot] = ItemStack.EMPTY;
        occupiedMask &= ~(1 << slot);
        loadNavigation();
        resumeReceivers();
        changed();
        return removed;
    }

    /** Called once by breakBlock. Clear ownership before entities are spawned so a
     * second callback cannot duplicate drops. Transfers intentionally bypass it.
     */
    public NonNullList<ItemStack> drainModules() {
        NonNullList<ItemStack> result = NonNullList.create();
        if (inert()) return result;
        if (world != null && (world.isRemote || world instanceof VehicleWorld || isInvalid()
                || !world.isBlockLoaded(pos) || world.getTileEntity(pos) != this)) return result;
        suspendReceivers();
        saveNavigation();
        for (int slot = 0; slot < modules.length; slot++) {
            if (!modules[slot].isEmpty()) result.add(modules[slot]);
            modules[slot] = ItemStack.EMPTY;
        }
        loadNavigation();
        if (occupiedMask != 0) {
            occupiedMask = renderMask = 0;
            revision = next(revision);
            output.publish();
            markDirty();
        }
        return result;
    }

    private static long next(long value) { return value == Long.MAX_VALUE ? 1 : value + 1; }

    @Override public void changed() {
        if (loadingModules || inert()) return;
        saveNavigation();
        revision = next(revision);
        renderMask = occupiedMask;
        output.publish();
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    /** Only the client animation manager changes this mask, at start/settlement.
     * A steady rack keeps the same mask and never requests another mesh rebuild.
     */
    public void setRenderMask(int mask) {
        mask &= occupiedMask;
        if (renderMask == mask) return;
        renderMask = mask;
        if (world != null && world.isRemote && !(world instanceof VehicleWorld))
            world.markBlockRangeForRenderUpdate(pos, pos);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        if (inert()) { tag.merge(failedLoad.copy()); return tag; }
        saveNavigation();
        writeTileIdentity(tag);
        NBTTagList items = new NBTTagList();
        for (int slot = 0; slot < modules.length; slot++) {
            if (modules[slot].isEmpty()) continue;
            NBTTagCompound item = modules[slot].writeToNBT(new NBTTagCompound());
            item.setByte("Slot", (byte)slot);
            items.appendTag(item);
        }
        tag.setTag("Items", items);
        if (housingTexture >= 0) tag.setInteger("RackHousing", housingTexture);
        tag.setBoolean("RackPowerOut",powerOut);if (emitRedstone) tag.setBoolean("RackEmit", true);
        if(legacyOriginal!=null)tag.setTag(com.vandorlabs.rack.LegacyAutopilotMigration.BACKUP,legacyOriginal.copy());
        writeVisualData(tag);
        return legacyBlockData==null?tag:legacyBlockData.preserve(tag);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        NBTTagCompound original = tag.copy();
        failedLoad = original;
        try {
        if (tag.hasKey("RackHousing") && (!tag.hasKey("RackHousing",3) || tag.getInteger("RackHousing") < -1
                || tag.getInteger("RackHousing") >= 0 && !ScreenHousingTextures.validChoice(tag.getInteger("RackHousing"))))
            throw new IllegalArgumentException("Invalid saved Rack housing");
        if (tag.hasKey("RackEmit") && (!tag.hasKey("RackEmit",1) || tag.getByte("RackEmit") < 0 || tag.getByte("RackEmit") > 1))
            throw new IllegalArgumentException("Invalid saved Rack output");
        if(tag.hasKey("RackPowerOut")&&(!tag.hasKey("RackPowerOut",1)||tag.getByte("RackPowerOut")<0||tag.getByte("RackPowerOut")>1))throw new IllegalArgumentException("Invalid Rack power direction");
        for (String key : new String[]{"Revision","Insert0","Insert1","Insert2","Insert3"})
            if (tag.hasKey(key) && (!tag.hasKey(key,4) || tag.getLong(key)<0)) throw new IllegalArgumentException("Invalid saved Rack sequence");
        if (tag.hasKey("Items") && (!tag.hasKey("Items",9)
                || ((NBTTagList)tag.getTag("Items")).tagCount()>4
                || ((NBTTagList)tag.getTag("Items")).tagCount()>0 && ((NBTTagList)tag.getTag("Items")).getTagType()!=10))
            throw new IllegalArgumentException("Invalid saved Rack inventory");
        ItemStack[] staged = {ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
        NBTTagList items = tag.getTagList("Items",10);
        for (int index=0;index<items.tagCount();index++) {
            NBTTagCompound row=items.getCompoundTagAt(index);
            if (!row.hasKey("Slot",1)) throw new IllegalArgumentException("Invalid saved Rack slot");
            int slot=row.getByte("Slot")&255;
            if (!ModuleRackGeometry.validSlot(slot) || !staged[slot].isEmpty()) throw new IllegalArgumentException("Duplicate or invalid saved Rack slot");
            ItemStack stack=new ItemStack(row);NBTTagCompound raw=row.copy();raw.removeTag("Slot");
            if (!accepts(stack) || stack.getCount()!=1 || !stack.writeToNBT(new NBTTagCompound()).equals(raw))
                throw new IllegalArgumentException("Unknown saved Rack module owner");
            if (stack.getItem()==ModItems.AUTOPILOT_MODULE) com.vandorlabs.rack.ModuleAutopilotData.settings(stack);
            staged[slot]=stack;
        }
        super.readFromNBT(tag);
        housingTexture = tag.hasKey("RackHousing", 3) && ScreenHousingTextures.validChoice(tag.getInteger("RackHousing")) ? tag.getInteger("RackHousing") : -1;
        emitRedstone = tag.getBoolean("RackEmit");
        powerOut=tag.getBoolean("RackPowerOut");
        output.loaded(tag);
        int occupied = 0;
        for (int slot=0;slot<4;slot++) { modules[slot]=staged[slot];if(!staged[slot].isEmpty())occupied|=1<<slot; }
        occupiedMask = renderMask = occupied;
        loadNavigation();
        revision = Math.max(0, tag.getLong("Revision"));
        for (int slot = 0; slot < modules.length; slot++)
            insertionSequences[slot] = Math.max(0, tag.getLong("Insert" + slot));
        legacyOriginal=tag.hasKey(com.vandorlabs.rack.LegacyAutopilotMigration.BACKUP,10)?tag.getCompoundTag(com.vandorlabs.rack.LegacyAutopilotMigration.BACKUP).copy():null;
        legacyBlockData=com.vandorlabs.rack.LegacyAutopilotMigration.preservation(tag,writeTileIdentity(new NBTTagCompound()));
        failedLoad=null;
        } catch (RuntimeException failure) {
            failedLoad=original;java.util.Arrays.fill(modules,ItemStack.EMPTY);
            occupiedMask=renderMask=moduleKinds=previousModuleKinds=0;primary=-1;
            loadingModules=true;try{readAutopilotData(new NBTTagCompound());}finally{loadingModules=false;}
        }
    }

    private NBTTagCompound writeVisualData(NBTTagCompound tag) {
        tag.setInteger("ModuleMask", occupiedMask);
        if (moduleKinds != com.vandorlabs.rack.ModuleRackPolicy.blankKinds(occupiedMask)) tag.setInteger("ModuleKinds", moduleKinds);
        for(int slot=0;slot<4;slot++)if(moduleKind(slot)==3)tag.setInteger("BatteryCharge"+slot,com.vandorlabs.rack.ModuleBatteryData.energy(modules[slot]));
        tag.setLong("Revision", revision);
        for (int slot = 0; slot < modules.length; slot++) tag.setLong("Insert" + slot, insertionSequences[slot]);
        return tag;
    }

    private NBTTagCompound writeSyncData(NBTTagCompound tag) {
        writeVisualData(tag);
        tag.setBoolean("RackInert",inert());
        if (housingTexture >= 0) tag.setInteger("RackHousing", housingTexture);
        tag.setBoolean("RackPowerOut",powerOut);if (emitRedstone) tag.setBoolean("RackEmit", true);
        NBTTagCompound transmission = output.publicSettings();
        if (transmission != null) tag.setTag("RackTransmission", transmission);
        for (int slot = 0; slot < modules.length; slot++) if (moduleKind(slot) == com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT) {
            com.vandorlabs.rack.AutopilotModuleSettings config = moduleSettings(slot);
            NBTTagCompound data = new NBTTagCompound(); com.vandorlabs.redstone.ChannelData.write(data, config.channels);
            data.setInteger("AutopilotTrigger", config.activation); data.setString("RouteMarker", config.marker); data.setInteger("RouteMarkerMeta", config.markerMetadata);
            tag.setTag("ModuleConfig" + slot, data);
        }
        if (canDepart()) {
            tag.setBoolean("RackNavigationEnabled", enabled());
            tag.setString("RackNavigationLabel", navigationLabel(status));
        }
        tag.setLong("RackEpoch", localEpoch);
        return tag;
    }

    private static String navigationLabel(String value) {
        return value.length() > MAX_NAVIGATION_LABEL ? value.substring(0, MAX_NAVIGATION_LABEL) : value;
    }

    /** Chunk-load tags contain placement identity but never actual items or arbitrary item NBT. */
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = new NBTTagCompound();
        net.minecraft.util.ResourceLocation id = TileEntity.getKey(getClass());
        if (id != null) tag.setString("id", id.toString());
        tag.setInteger("x", pos.getX()); tag.setInteger("y", pos.getY()); tag.setInteger("z", pos.getZ());
        return writeSyncData(tag);
    }

    @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, writeSyncData(new NBTTagCompound()));
    }

    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        // A newly placed client tile can receive its first packet only when a
        // player inserts. That live event must animate from the empty baseline;
        // only handleUpdateTag identifies an already-populated chunk load.
        readVisualData(packet.getNbtCompound(), false);
    }

    @Override public void handleUpdateTag(NBTTagCompound tag) { readVisualData(tag, true); }

    private void readVisualData(NBTTagCompound tag, boolean initial) {
        long epoch = Math.max(0, tag.getLong("RackEpoch"));
        // Block-change packets can coalesce a remove/place pair at one position,
        // leaving the old client tile alive. Revisions order updates only within
        // their original server instance; the replacement starts a fresh epoch.
        // Server packets are ordered, and an older epoch must never revive a
        // retired instance after a replacement has already been observed.
        if (epoch > 0 && receivedEpoch > 0 && epoch < receivedEpoch) return;
        boolean replacement = epoch > 0 && epoch != receivedEpoch;
        long incoming = Math.max(0, tag.getLong("Revision"));
        // Retried old updates cannot rewind settled state or replay insertions.
        if (!replacement && !initial && incoming < revision
                && !(revision == Long.MAX_VALUE && incoming == 1)) return;
        int previousMask = replacement ? 0 : occupiedMask;
        int mask = tag.getInteger("ModuleMask") & ModuleRackGeometry.ALL_SLOTS;
        boolean changed = replacement || occupiedMask != mask || incoming != revision;
        if (!initial && !changed) return;
        long[] previousSequences = replacement ? new long[ModuleRackGeometry.SLOTS] : insertionSequences.clone();
        if (replacement) {
            receivedEpoch = epoch;
            renderMask = 0;
        }
        occupiedMask = mask;
        failedLoad = tag.getBoolean("RackInert") ? new NBTTagCompound() : null;
        previousBatteryPixels=batteryChargeLevels();
        previousModuleKinds = moduleKinds;
        moduleKinds = tag.hasKey("ModuleKinds",3) ? tag.getInteger("ModuleKinds") & 4095 : com.vandorlabs.rack.ModuleRackPolicy.blankKinds(mask);
        for (int slot = 0; slot < modules.length; slot++) {
            if (moduleKind(slot) == com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT) {
                modules[slot] = new ItemStack(ModItems.AUTOPILOT_MODULE);
                com.vandorlabs.rack.ModuleAutopilotData.state(modules[slot],tag.getCompoundTag("ModuleConfig" + slot));
            } else if(moduleKind(slot)==4){
                modules[slot]=new ItemStack(ModItems.SPEED_MODULE);
            } else if(moduleKind(slot)==3){
                modules[slot]=new ItemStack(ModItems.BATTERY_MODULE);
                com.vandorlabs.rack.ModuleBatteryData.receive(modules[slot],Math.max(0,Math.min(com.vandorlabs.rack.ModuleBatteryData.CAPACITY,tag.getInteger("BatteryCharge"+slot))),false);
            } else modules[slot] = ItemStack.EMPTY;
        }
        String label = canDepart() && tag.hasKey("RackNavigationLabel", 8)
                ? navigationLabel(tag.getString("RackNavigationLabel")) : "Off";
        readAutopilotVisual(canDepart() && tag.getBoolean("RackNavigationEnabled"), label);
        housingTexture = tag.hasKey("RackHousing", 3) && ScreenHousingTextures.validChoice(tag.getInteger("RackHousing")) ? tag.getInteger("RackHousing") : -1;
        emitRedstone = tag.getBoolean("RackEmit");
        powerOut=tag.getBoolean("RackPowerOut");
        output.readPublic(tag, replacement || initial);
        revision = incoming;
        for (int slot = 0; slot < modules.length; slot++)
            insertionSequences[slot] = Math.max(0, tag.getLong("Insert" + slot));
        if (initial) renderMask = occupiedMask;
        else renderMask &= occupiedMask;
        if (VandorLabs.proxy != null)
            VandorLabs.proxy.moduleRackUpdated(this, previousMask, previousSequences, initial);
        else renderMask = occupiedMask;
        if (world != null && changed) world.markBlockRangeForRenderUpdate(pos, pos);
    }

    @Override public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override public void onLoad() {
        super.onLoad();
        if (!(world instanceof VehicleWorld)) DeferredTileLoad.schedule(this, () -> { if(!inert()){com.vandorlabs.redstone.RedstoneChannels.register(output); resumeReceivers(); output.publish();} });
    }

    @Override public void invalidate() {
        suspendReceivers();
        com.vandorlabs.redstone.RedstoneChannels.unregister(output);
        if (world != null && world.isRemote && !(world instanceof VehicleWorld) && VandorLabs.proxy != null)
            VandorLabs.proxy.moduleRackRemoved(this);
        super.invalidate();
    }

    @Override public void onChunkUnload() {
        suspendReceivers();
        com.vandorlabs.redstone.RedstoneChannels.unregister(output);
        if (world != null && world.isRemote && !(world instanceof VehicleWorld) && VandorLabs.proxy != null)
            VandorLabs.proxy.moduleRackRemoved(this);
        super.onChunkUnload();
    }


    public int moduleKind(int slot) { return com.vandorlabs.rack.ModuleRackPolicy.flightSlotKind(moduleKinds,slot); }
    public int previousModuleKind(int slot) { return com.vandorlabs.rack.ModuleRackPolicy.flightSlotKind(previousModuleKinds,slot); }
    public int getModuleKinds() { return moduleKinds; }
    public int autopilotCount() { int count=0; for(int slot=0;slot<4;slot++) if(moduleKind(slot)==com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT) count++; return count; }
    public int primaryModuleSlot() { return primary; }
    @Override protected boolean hasNavigationReceiver() { return primary >= 0; }
    @Override protected boolean canDepart() { return autopilotCount() == 1; }
    @Override public boolean enabled() { return canDepart() && super.enabled(); }
    @Override public String transmitterSlot() { return "module_" + primary; }
    @Override public com.vandorlabs.redstone.ChannelList transmittedChannels() { return com.vandorlabs.redstone.ChannelList.EMPTY; }
    @Override public int transmissionRange() { return -1; }
    public com.vandorlabs.rack.AutopilotModuleSettings moduleSettings(int slot) {
        if(moduleKind(slot)!=com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT) throw new IllegalArgumentException("No Autonavigation Module in slot");
        return com.vandorlabs.rack.ModuleAutopilotData.settings(modules[slot]);
    }
    public boolean configureModule(int slot,long sequence,com.vandorlabs.rack.AutopilotModuleSettings value) {
        if(!canMutate() || moduleKind(slot)!=com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT || getInsertionSequence(slot)!=sequence) return false;
        suspendReceivers(); saveNavigation(); com.vandorlabs.rack.ModuleAutopilotData.configure(modules[slot],value);
        loadNavigation(); resumeReceivers(); changed(); return true;
    }
    private void saveNavigation() {
        if(primary>=0 && !loadingModules && (world==null || !world.isRemote || world instanceof VehicleWorld)) {
            // The physical item owns navigation state, including unknown extension fields.
            // Clear optional live references before overlaying known state so relocation cannot
            // resurrect an old departure authorization from the preserved module data.
            NBTTagCompound saved=com.vandorlabs.rack.ModuleAutopilotData.state(modules[primary]);
            for(String key:new String[]{"AutopilotDepartureSeat","AutopilotDepartureOwnerMost","AutopilotDepartureOwnerLeast","AutopilotDepartureWaypoint"})saved.removeTag(key);
            com.vandorlabs.rack.ModuleAutopilotData.state(modules[primary],writeAutopilotData(saved));
        }
    }
    private void loadNavigation() {
        loadingModules=true;
        try {
            moduleKinds=0; primary=-1;
            for(int slot=0;slot<4;slot++) if(!modules[slot].isEmpty()) {
                int kind=modules[slot].getItem()==ModItems.AUTOPILOT_MODULE?com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT:modules[slot].getItem()==ModItems.BATTERY_MODULE?com.vandorlabs.rack.ModuleRackPolicy.BATTERY:com.vandorlabs.rack.ModuleRackPolicy.BLANK;
                moduleKinds |= kind << (slot*2);
                if(modules[slot].getItem()==ModItems.SPEED_MODULE)moduleKinds |= 1 << (8+slot); if(kind==com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT && primary<0) primary=slot;
            }
            readAutopilotData(primary<0?new NBTTagCompound():com.vandorlabs.rack.ModuleAutopilotData.state(modules[primary]));
        } finally { loadingModules=false; }
    }
    private boolean physicalChannels() { return !inert() && world!=null && !world.isRemote && !(world instanceof VehicleWorld) && !isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos)==this; }
    private void suspendReceivers() {
        if(world==null||world.isRemote||world instanceof VehicleWorld)return;
        com.vandorlabs.redstone.RedstoneChannels.unregister(this);
        for(ModuleReceiver receiver:receivers) com.vandorlabs.redstone.RedstoneChannels.unregister(receiver);
    }
    private void resumeReceivers() {
        if(!physicalChannels())return;
        if(primary>=0)com.vandorlabs.redstone.RedstoneChannels.register(this);
        for(int slot=0;slot<4;slot++) if(slot!=primary && moduleKind(slot)==com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT)
            com.vandorlabs.redstone.RedstoneChannels.register(receivers[slot]);
        if(canDepart()&&super.enabled()&&departureAt>0)world.scheduleUpdate(pos,getBlockType(),1);
    }
    public java.util.List<com.vandorlabs.redstone.RedstoneChannelMember> moduleReceivers() {
        java.util.List<com.vandorlabs.redstone.RedstoneChannelMember> result=new java.util.ArrayList<>();
        if(primary>=0)result.add(this);
        for(int slot=0;slot<4;slot++)if(slot!=primary && moduleKind(slot)==com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT)result.add(receivers[slot]);
        return result;
    }
    @Override public void refreshPower() {
        if(!physicalChannels())return;
        super.refreshPower();
        for(int slot=0;slot<4;slot++)if(slot!=primary && moduleKind(slot)==com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT)
            com.vandorlabs.redstone.RedstoneChannels.inputChanged(receivers[slot]);
    }
    private final class ModuleReceiver implements com.vandorlabs.redstone.RedstoneChannelMember {
        private final int slot; ModuleReceiver(int slot){this.slot=slot;}
        public TileEntity channelTile(){return TileEntityModuleRack.this;}
        public String transmitterSlot(){return "module_"+slot;}
        public com.vandorlabs.redstone.ChannelList transmittedChannels(){return com.vandorlabs.redstone.ChannelList.EMPTY;}
        public int transmissionRange(){return -1;}
        public int getRedstoneChannel(){return getRedstoneChannels().first();}
        public com.vandorlabs.redstone.ChannelList getRedstoneChannels(){return moduleKind(slot)==com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT?moduleSettings(slot).channels:com.vandorlabs.redstone.ChannelList.EMPTY;}
        public void setRedstoneChannel(int channel){throw new UnsupportedOperationException();}
        public boolean hasLocalRedstoneSignal(){return TileEntityModuleRack.this.hasLocalRedstoneSignal();}
        public void setChannelSignal(boolean powered){if(com.vandorlabs.rack.ModuleAutopilotData.signal(modules[slot],powered))changed();}
    }

    private int previousBatteryPixels;
    public int batteryPixels(int slot){return com.vandorlabs.rack.BatteryChargeBar.unpack(batteryChargeLevels(),slot);}
    public int previousBatteryPixels(int slot){return com.vandorlabs.rack.BatteryChargeBar.unpack(previousBatteryPixels,slot);}
    public int batteryChargeLevels(){int result=0;for(int slot=0;slot<4;slot++)result|=com.vandorlabs.rack.BatteryChargeBar.pixels(com.vandorlabs.rack.ModuleBatteryData.energy(modules[slot]),com.vandorlabs.rack.ModuleBatteryData.CAPACITY)<<(slot*6);return result;}
    public int batteryEnergy(){int total=0;for(ItemStack stack:modules)total+=com.vandorlabs.rack.ModuleBatteryData.energy(stack);return total;}
    public int batteryCapacity(){int total=0;for(ItemStack stack:modules)if(com.vandorlabs.rack.ModuleBatteryData.valid(stack))total+=com.vandorlabs.rack.ModuleBatteryData.CAPACITY;return total;}
    public int receiveBatteryEnergy(int maximum,boolean simulate){return transferBattery(maximum,simulate,true);}
    public int extractBatteryEnergy(int maximum,boolean simulate){return transferBattery(maximum,simulate,false);}
    private int transferBattery(int maximum,boolean simulate,boolean receive){
        if(!canMutate())return 0;int previousLevels=batteryChargeLevels(),total=moveBattery(maximum,simulate,receive);
        if(!simulate&&total>0){markDirty();if(previousLevels!=batteryChargeLevels())changed();}return total;
    }
    private int moveBattery(int maximum,boolean simulate,boolean receive){
        int remaining=Math.max(0,maximum),total=0;
        for(ItemStack stack:modules){int amount=receive?com.vandorlabs.rack.ModuleBatteryData.receive(stack,remaining,simulate):com.vandorlabs.rack.ModuleBatteryData.extract(stack,remaining,simulate);remaining-=amount;total+=amount;}
        return total;
    }
    /** A carried rack cannot change its modules, but its vehicle may spend Battery Module charge
     * on onboard observers. The items keep owning the charge, so the dirty tile is saved into the
     * vehicle snapshot and parks with the rack.
     */
    public int extractCarriedBatteryEnergy(int maximum,boolean simulate){
        if(inert()||!com.vandorlabs.vehicle.VehicleResources.live(this))return 0;
        int total=moveBattery(maximum,simulate,false);
        if(!simulate&&total>0)markDirty();
        return total;
    }

    @Override public boolean shouldRenderInPass(int pass) { return false; }
    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability,net.minecraft.util.EnumFacing side){return !inert()&&(capability==net.minecraftforge.energy.CapabilityEnergy.ENERGY&&powerAvailable(side)||super.hasCapability(capability,side));}
    @Override public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability,net.minecraft.util.EnumFacing side){return inert()?null:capability==net.minecraftforge.energy.CapabilityEnergy.ENERGY&&powerAvailable(side)?net.minecraftforge.energy.CapabilityEnergy.ENERGY.cast(batteryPort):super.getCapability(capability,side);}
}
