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
public final class TileEntityModuleRack extends TileEntity {
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
        return !stack.isEmpty() && stack.getItem() == ModItems.BLANK_MODULE;
    }

    /** Offline copies may be edited by validation/import code; live clients and carried
     * read-only views cannot acquire or release physical inventory through this API.
     */
    private boolean canMutate() {
        return world == null || !(world instanceof VehicleWorld) && !world.isRemote
                && !isInvalid() && world.getTileEntity(pos) == this;
    }

    public boolean insertModule(int slot, ItemStack held, boolean creative) {
        if (!canMutate() || !ModuleRackGeometry.validSlot(slot)
                || !modules[slot].isEmpty() || !accepts(held)) return false;
        ItemStack inserted = held.copy();
        inserted.setCount(1);
        modules[slot] = inserted;
        if (!creative) held.shrink(1);
        occupiedMask |= 1 << slot;
        insertionSequences[slot] = next(insertionSequences[slot]);
        changed();
        return true;
    }

    public ItemStack removeModule(int slot) {
        if (!canMutate() || !ModuleRackGeometry.validSlot(slot)) return ItemStack.EMPTY;
        ItemStack removed = modules[slot];
        if (removed.isEmpty()) return ItemStack.EMPTY;
        modules[slot] = ItemStack.EMPTY;
        occupiedMask &= ~(1 << slot);
        changed();
        return removed;
    }

    /** Called once by breakBlock. Clear ownership before entities are spawned so a
     * second callback cannot duplicate drops. Transfers intentionally bypass it.
     */
    public NonNullList<ItemStack> drainModules() {
        NonNullList<ItemStack> result = NonNullList.create();
        if (world != null && (world.isRemote || world instanceof VehicleWorld)) return result;
        for (int slot = 0; slot < modules.length; slot++) {
            if (!modules[slot].isEmpty()) result.add(modules[slot]);
            modules[slot] = ItemStack.EMPTY;
        }
        if (occupiedMask != 0) {
            occupiedMask = renderMask = 0;
            revision = next(revision);
            markDirty();
        }
        return result;
    }

    private static long next(long value) { return value == Long.MAX_VALUE ? 1 : value + 1; }

    private void changed() {
        revision = next(revision);
        renderMask = occupiedMask;
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
        super.writeToNBT(tag);
        NBTTagList items = new NBTTagList();
        for (int slot = 0; slot < modules.length; slot++) {
            if (modules[slot].isEmpty()) continue;
            NBTTagCompound item = modules[slot].writeToNBT(new NBTTagCompound());
            item.setByte("Slot", (byte)slot);
            items.appendTag(item);
        }
        tag.setTag("Items", items);
        return writeVisualData(tag);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
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
        revision = Math.max(0, tag.getLong("Revision"));
        for (int slot = 0; slot < modules.length; slot++)
            insertionSequences[slot] = Math.max(0, tag.getLong("Insert" + slot));
    }

    private NBTTagCompound writeVisualData(NBTTagCompound tag) {
        tag.setInteger("ModuleMask", occupiedMask);
        tag.setLong("Revision", revision);
        for (int slot = 0; slot < modules.length; slot++) tag.setLong("Insert" + slot, insertionSequences[slot]);
        return tag;
    }

    private NBTTagCompound writeSyncData(NBTTagCompound tag) {
        writeVisualData(tag);
        tag.setLong("RackEpoch", localEpoch);
        return tag;
    }

    /** Chunk-load tags contain placement identity but never actual items or arbitrary item NBT. */
    @Override public NBTTagCompound getUpdateTag() {
        return writeSyncData(super.writeToNBT(new NBTTagCompound()));
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

    @Override public void invalidate() {
        if (world != null && world.isRemote && !(world instanceof VehicleWorld) && VandorLabs.proxy != null)
            VandorLabs.proxy.moduleRackRemoved(this);
        super.invalidate();
    }

    @Override public void onChunkUnload() {
        if (world != null && world.isRemote && !(world instanceof VehicleWorld) && VandorLabs.proxy != null)
            VandorLabs.proxy.moduleRackRemoved(this);
        super.onChunkUnload();
    }

    @Override public boolean shouldRenderInPass(int pass) { return false; }
}
