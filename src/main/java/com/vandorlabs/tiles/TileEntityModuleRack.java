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
public final class TileEntityModuleRack extends TileEntityAutopilot {
    private static final AtomicLong NEXT_EPOCH = new AtomicLong();
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
    private com.vandorlabs.rack.LegacyAutopilotData legacyBlockData;
    private NBTTagCompound legacyOriginal;
    private final ModuleReceiver[] receivers = { new ModuleReceiver(0), new ModuleReceiver(1), new ModuleReceiver(2), new ModuleReceiver(3) };
    private int housingTexture = -1;
    private boolean emitRedstone;
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
        return !stack.isEmpty() && (stack.getItem() == ModItems.BLANK_MODULE || stack.getItem() == ModItems.AUTOPILOT_MODULE);
    }

    /** Offline copies may be edited by validation/import code; live clients and carried
     * read-only views cannot acquire or release physical inventory through this API.
     */
    private boolean canMutate() {
        return world == null || !(world instanceof VehicleWorld) && !world.isRemote
                && !isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this;
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
        if (loadingModules) return;
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
        if (emitRedstone) tag.setBoolean("RackEmit", true);
        if(legacyOriginal!=null)tag.setTag(com.vandorlabs.rack.LegacyAutopilotMigration.BACKUP,legacyOriginal.copy());
        writeVisualData(tag);
        return legacyBlockData==null?tag:legacyBlockData.preserve(tag);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        housingTexture = tag.hasKey("RackHousing", 3) && ScreenHousingTextures.validChoice(tag.getInteger("RackHousing")) ? tag.getInteger("RackHousing") : -1;
        emitRedstone = tag.getBoolean("RackEmit");
        output.loaded(tag);
        for (int slot = 0; slot < modules.length; slot++) modules[slot] = ItemStack.EMPTY;
        NBTTagList items = tag.getTagList("Items", 10);
        int occupied = 0;
        for (int index = 0; index < items.tagCount(); index++) {
            NBTTagCompound item = items.getCompoundTagAt(index);
            int slot = item.getByte("Slot") & 255;
            if (!ModuleRackGeometry.validSlot(slot) || !modules[slot].isEmpty()) continue;
            ItemStack stack = new ItemStack(item);
            if (!accepts(stack)) continue;
            stack.setCount(1);
            modules[slot] = stack;
            occupied |= 1 << slot;
        }
        occupiedMask = renderMask = occupied;
        loadNavigation();
        revision = Math.max(0, tag.getLong("Revision"));
        for (int slot = 0; slot < modules.length; slot++)
            insertionSequences[slot] = Math.max(0, tag.getLong("Insert" + slot));
        legacyOriginal=tag.hasKey(com.vandorlabs.rack.LegacyAutopilotMigration.BACKUP,10)?tag.getCompoundTag(com.vandorlabs.rack.LegacyAutopilotMigration.BACKUP).copy():null;
        legacyBlockData=com.vandorlabs.rack.LegacyAutopilotMigration.preservation(tag,writeTileIdentity(new NBTTagCompound()));
    }

    private NBTTagCompound writeVisualData(NBTTagCompound tag) {
        tag.setInteger("ModuleMask", occupiedMask);
        if (autopilotCount() > 0) tag.setInteger("ModuleKinds", moduleKinds);
        tag.setLong("Revision", revision);
        for (int slot = 0; slot < modules.length; slot++) tag.setLong("Insert" + slot, insertionSequences[slot]);
        return tag;
    }

    private NBTTagCompound writeSyncData(NBTTagCompound tag) {
        writeVisualData(tag);
        if (housingTexture >= 0) tag.setInteger("RackHousing", housingTexture);
        if (emitRedstone) tag.setBoolean("RackEmit", true);
        NBTTagCompound transmission = output.publicSettings();
        if (transmission != null) tag.setTag("RackTransmission", transmission);
        for (int slot = 0; slot < modules.length; slot++) if (moduleKind(slot) == com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT) {
            com.vandorlabs.rack.AutopilotModuleSettings config = moduleSettings(slot);
            NBTTagCompound data = new NBTTagCompound(); com.vandorlabs.redstone.ChannelData.write(data, config.channels);
            data.setInteger("AutopilotTrigger", config.activation); data.setString("RouteMarker", config.marker); data.setInteger("RouteMarkerMeta", config.markerMetadata);
            tag.setTag("ModuleConfig" + slot, data);
        }
        tag.setLong("RackEpoch", localEpoch);
        return tag;
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
        previousModuleKinds = moduleKinds;
        moduleKinds = tag.hasKey("ModuleKinds",3) ? tag.getInteger("ModuleKinds") & 255 : com.vandorlabs.rack.ModuleRackPolicy.blankKinds(mask);
        for (int slot = 0; slot < modules.length; slot++) {
            if (moduleKind(slot) == com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT) {
                modules[slot] = new ItemStack(ModItems.AUTOPILOT_MODULE);
                com.vandorlabs.rack.ModuleAutopilotData.state(modules[slot],tag.getCompoundTag("ModuleConfig" + slot));
            } else modules[slot] = ItemStack.EMPTY;
        }
        housingTexture = tag.hasKey("RackHousing", 3) && ScreenHousingTextures.validChoice(tag.getInteger("RackHousing")) ? tag.getInteger("RackHousing") : -1;
        emitRedstone = tag.getBoolean("RackEmit");
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
        if (!(world instanceof VehicleWorld)) DeferredTileLoad.schedule(this, () -> { com.vandorlabs.redstone.RedstoneChannels.register(output); resumeReceivers(); output.publish(); });
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


    public int moduleKind(int slot) { return ModuleRackGeometry.validSlot(slot) ? (moduleKinds >>> (slot * 2)) & 3 : 0; }
    public int previousModuleKind(int slot) { return (previousModuleKinds >>> (slot*2)) & 3; }
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
        if(moduleKind(slot)!=com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT) throw new IllegalArgumentException("No Autopilot module in slot");
        return com.vandorlabs.rack.ModuleAutopilotData.settings(modules[slot]);
    }
    public boolean configureModule(int slot,long sequence,com.vandorlabs.rack.AutopilotModuleSettings value) {
        if(!canMutate() || moduleKind(slot)!=com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT || getInsertionSequence(slot)!=sequence) return false;
        suspendReceivers(); saveNavigation(); com.vandorlabs.rack.ModuleAutopilotData.configure(modules[slot],value);
        loadNavigation(); resumeReceivers(); changed(); return true;
    }
    private void saveNavigation() {
        if(primary>=0 && !loadingModules && (world==null || !world.isRemote || world instanceof VehicleWorld))
            com.vandorlabs.rack.ModuleAutopilotData.state(modules[primary],writeAutopilotData(new NBTTagCompound()));
    }
    private void loadNavigation() {
        loadingModules=true;
        try {
            moduleKinds=0; primary=-1;
            for(int slot=0;slot<4;slot++) if(!modules[slot].isEmpty()) {
                int kind=modules[slot].getItem()==ModItems.AUTOPILOT_MODULE?com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT:com.vandorlabs.rack.ModuleRackPolicy.BLANK;
                moduleKinds |= kind << (slot*2); if(kind==com.vandorlabs.rack.ModuleRackPolicy.AUTOPILOT && primary<0) primary=slot;
            }
            readAutopilotData(primary<0?new NBTTagCompound():com.vandorlabs.rack.ModuleAutopilotData.state(modules[primary]));
        } finally { loadingModules=false; }
    }
    private boolean physicalChannels() { return world!=null && !world.isRemote && !(world instanceof VehicleWorld) && !isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos)==this; }
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

    @Override public boolean shouldRenderInPass(int pass) { return false; }
}
