package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockShipSystem;
import com.vandorlabs.shipsystems.ShipSystemMesh;
import com.vandorlabs.shipsystems.MountFrame;
import com.vandorlabs.redstone.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public final class TileEntityShipSystem extends TileEntity implements ITickable, RedstoneChannelMember {
    public static final int DISABLED = 0, REDSTONE_ON = 1, REDSTONE_OFF = 2;
    private ChannelList channels = ChannelList.EMPTY;
    private int redstoneMode = DISABLED;
    private boolean manualOn = true, channelSignal;
    private int lastLocalLevel;
    public BlockPos anchor;
    public EnumFacing mount=EnumFacing.UP;
    public MountFrame frame() { return new MountFrame(facing(),mount); }
    public boolean removing;
    public boolean active;
    public BlockPos consoleOwner;
    public int consoleWidth = 1, consoleIndex, consoleDepth = 1, consoleRow;
    private boolean firstTick = true;
    public int getRedstoneMode() { return redstoneMode; }
    @Override public TileEntity channelTile() { return this; }
    @Override public ChannelList getRedstoneChannels() { return channels; }
    @Override public int getRedstoneChannel() { return channels.first(); }
    @Override public void setRedstoneChannel(int channel) { setRedstoneChannels(ChannelList.of(Math.max(0, channel))); }
    @Override public void setRedstoneChannels(ChannelList next) { configure(next, redstoneMode); }
    public TileEntityShipSystem configurationOwner() {
        if (world == null || block() == null) return null;
        TileEntityShipSystem base = isAnchor() ? this : at(world, anchor);
        if (base == null || base.block() != block() || !base.isAnchor() || base.facing() != facing() || base.mount != mount) return null;
        if (!block().isConsole() || base.consoleOwner == null) return base;
        TileEntityShipSystem owner = at(world, base.consoleOwner);
        return owner != null && owner.isAnchor() && owner.block() == block() && owner.facing() == facing() && owner.mount == mount
                && owner.consoleIndex == 0 && owner.consoleRow == 0 && owner.consoleWidth == base.consoleWidth
                && owner.consoleDepth == base.consoleDepth
                && owner.frame().cell(owner.getPos(),base.consoleIndex,0,base.consoleRow).equals(base.getPos()) ? owner : null;
    }
    private java.util.List<TileEntityShipSystem> configurationMembers() {
        java.util.List<TileEntityShipSystem> result = new java.util.ArrayList<>();
        if (configurationOwner() != this) return result;
        int width = block().isConsole() ? consoleWidth : 1;
        int depth = block().isConsole() ? consoleDepth : 1;
        for (int row = 0; row < depth; row++) for (int i = 0; i < width; i++) {
            TileEntityShipSystem member = at(world, frame().cell(pos,i,0,row));
            if (member == null || member.configurationOwner() != this || !member.formed()) return java.util.Collections.emptyList();
            result.add(member);
        }
        return result;
    }
    public boolean canConfigure(net.minecraft.entity.player.EntityPlayer player) {
        java.util.List<TileEntityShipSystem> members = configurationMembers();
        if (members.isEmpty()) return false;
        for (TileEntityShipSystem member : members) for (BlockPos cell : block().cells(member.pos, facing(),mount))
            if (!world.isBlockModifiable(player, cell) || !player.canPlayerEdit(cell, EnumFacing.UP, player.getHeldItemMainhand())) return false;
        return true;
    }
    public void configure(ChannelList next, int mode) {
        if (next == null || mode < DISABLED || mode > REDSTONE_OFF || world == null || world.isRemote) return;
        java.util.List<TileEntityShipSystem> members = configurationMembers();
        java.util.Map<TileEntityShipSystem, ChannelList> previous = new java.util.LinkedHashMap<>();
        for (TileEntityShipSystem member : members) {
            previous.put(member, member.channels);
            if (mode == DISABLED && member.redstoneMode != DISABLED) member.manualOn = active;
            member.channels = next; member.redstoneMode = mode;
            member.channelSignal = false;
        }
        for (TileEntityShipSystem member : members) {
            RedstoneChannels.channelChanged(member, previous.get(member));
            member.refreshAppearance(); member.sync();
        }
    }
    public boolean toggleManual(net.minecraft.entity.player.EntityPlayer player) {
        if (redstoneMode != DISABLED || !canConfigure(player)) return false;
        boolean next = !active;
        for (TileEntityShipSystem member : configurationMembers()) { member.manualOn = next; member.markDirty(); }
        refreshAppearance();
        return true;
    }
    public boolean formed() {
        if (world == null || !isAnchor() || block() == null || removing) return false;
        for (BlockPos cell : block().cells(pos, facing(),mount)) {
            TileEntityShipSystem member = at(world, cell);
            if (member == null || member.block() != block() || !pos.equals(member.anchor) || member.facing() != facing() || member.mount != mount) return false;
        }
        return true;
    }
    @Override public boolean hasLocalRedstoneSignal() {
        return localSignalLevel(0) > 0;
    }
    @Override public int localSignalLevel(int channel) {
        if (!formed()) return 0;
        int level = 0;
        for (BlockPos cell : block().cells(pos, facing(),mount)) level = Math.max(level, LoadedRedstonePower.level(world, cell));
        return level;
    }
    @Override public void setChannelSignal(boolean powered) {
        if (channelSignal == powered) return;
        channelSignal = powered;
        if (world != null && !world.isRemote && isAnchor() && block() != null && !removing) applyAppearance();
    }
    public boolean requestedOn() {
        if (!formed()) return false;
        return evaluatePower(inputPowered());
    }
    public boolean inputPowered() { return hasLocalRedstoneSignal() || channelSignal; }
    public boolean evaluatePower(boolean powered) {
        return redstoneMode == DISABLED ? manualOn : redstoneMode == REDSTONE_OFF ? !powered : powered;
    }
    @Override public void onLoad() {
        super.onLoad();
        DeferredTileLoad.schedule(this, () -> { if (isAnchor()) { RedstoneChannels.register(this); refreshAppearance(); } });
    }
    @Override public void invalidate() { RedstoneChannels.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { RedstoneChannels.unregister(this); super.onChunkUnload(); }
    public static TileEntityShipSystem at(IBlockAccess w, BlockPos p) {
        if (w instanceof World && !((World)w).isBlockLoaded(p)) return null;
        TileEntity tile = w.getTileEntity(p);
        return tile instanceof TileEntityShipSystem ? (TileEntityShipSystem)tile : null;
    }
    public BlockShipSystem block() {
        return world != null && world.getBlockState(pos).getBlock() instanceof BlockShipSystem ? (BlockShipSystem)world.getBlockState(pos).getBlock() : null;
    }
    public String model() {
        BlockShipSystem b = block();
        if (b.isConsole()) return b.kind + "_" + consoleWidth + (consoleDepth > 1 ? "_depth_" + consoleDepth : "") + (visuallyActive() ? "_on" : "_off");
        return b.hasPowerStates() ? b.kind + (visuallyActive() ? "_on" : "_off") : b.itemModel();
    }
    public boolean visuallyActive() {
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld && com.vandorlabs.vehicle.VehicleWorld.isPropulsion(block()))
            return ((com.vandorlabs.vehicle.VehicleWorld)world).propulsionLevel>0;
        return active;
    }
    public EnumFacing facing() { return world.getBlockState(pos).getValue(BlockShipSystem.FACING); }
    public boolean isAnchor() { return anchor == null || anchor.equals(pos); }
    public boolean draws() { return isAnchor() && (block() == null || !block().isConsole() || consoleIndex == 0 && consoleRow == 0); }
    public void sync() { markDirty(); IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 2); }
    public void applyConsole(BlockPos owner, int index, int width, boolean powered) {
        applyConsole(owner, index, 0, width, 1, powered);
    }
    public void applyConsole(BlockPos owner, int index, int row, int width, int depth, boolean powered) {
        if (owner.equals(consoleOwner) && index == consoleIndex && row == consoleRow
                && width == consoleWidth && depth == consoleDepth && active == powered) return;
        consoleOwner = owner; consoleIndex = index; consoleRow = row;
        consoleWidth = width; consoleDepth = depth; active = powered;
        sync();
    }
    public void refreshAppearance() {
        if (world.isRemote || block() == null || !isAnchor()) return;
        RedstoneChannels.register(this);
        int local = localSignalLevel(0);
        if (local != lastLocalLevel) { lastLocalLevel = local; RedstoneChannels.inputChanged(this); }
        applyAppearance();
    }
    private void applyAppearance() {
        if (block().isConsole()) {
            com.vandorlabs.shipsystems.ConsoleConnections.refresh(world, pos);
        } else if (block().hasPowerStates()) {
            boolean powered = requestedOn();
            if (active != powered) { active = powered; sync(); }
        }
    }
    public void removeAssembly(IBlockState original) {
        if (removing) return;
        BlockPos owner = anchor == null ? pos : anchor;
        BlockShipSystem b = (BlockShipSystem)original.getBlock();
        // Mark every loaded member first to prevent recursive teardown and duplicate drops.
        for (BlockPos cell : b.cells(owner, original.getValue(BlockShipSystem.FACING),mount)) {
            TileEntityShipSystem member = at(world, cell);
            if (member != null && (cell.equals(pos) || owner.equals(member.anchor))) member.removing = true;
        }
        for (BlockPos cell : b.cells(owner, original.getValue(BlockShipSystem.FACING),mount)) {
            if (cell.equals(pos)) continue;
            TileEntityShipSystem member = at(world, cell);
            if (member != null && member.removing && world.getBlockState(cell).getBlock() == b) world.setBlockToAir(cell);
        }
        // The harvested tile still supplies the one survival drop.
        removing = false;
    }
    public void update() {
        if (world.isRemote || removing || block() == null) return;
        if (!firstTick && world.getTotalWorldTime() % 20 != 0) return;
        firstTick = false;
        if (!isAnchor()) {
            if (world.isBlockLoaded(anchor)) {
                TileEntityShipSystem owner = at(world, anchor);
                if (owner == null || !owner.isAnchor() || owner.block() != block() || owner.facing() != facing() || owner.mount != mount) {
                    removing = true; world.setBlockToAir(pos);
                }
            }
        } else {
            for (BlockPos cell : block().cells(pos, facing(),mount)) if (world.isBlockLoaded(cell)) {
                TileEntityShipSystem member = at(world, cell);
                if (member == null || member.block() != block() || !pos.equals(member.anchor) || member.mount != mount) {
                    removeAssembly(world.getBlockState(pos)); removing = true; world.setBlockToAir(pos); return;
                }
            }
            refreshAppearance();
        }
    }
    public NBTTagCompound writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        if (anchor != null) n.setLong("OwnerOffset", anchor.subtract(pos).toLong());
        if (consoleOwner != null) n.setLong("ConsoleOwnerOffset", consoleOwner.subtract(pos).toLong());
        n.setInteger("ConsoleWidth", consoleWidth);
        n.setInteger("ConsoleIndex", consoleIndex);
        n.setInteger("ConsoleDepth", consoleDepth);
        n.setInteger("ConsoleRow", consoleRow);
        n.setBoolean("Active", active);
        n.setInteger("MountFace",mount.getIndex());
        ChannelData.write(n, channels);
        n.setInteger("RedstoneMode", redstoneMode);
        n.setBoolean("ManualOn", manualOn);
        return n;
    }
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        anchor = n.hasKey("OwnerOffset") ? pos.add(BlockPos.fromLong(n.getLong("OwnerOffset"))) : null;
        consoleOwner = n.hasKey("ConsoleOwnerOffset") ? pos.add(BlockPos.fromLong(n.getLong("ConsoleOwnerOffset"))) : null;
        consoleWidth = Math.max(1, Math.min(3, n.getInteger("ConsoleWidth")));
        consoleIndex = Math.max(0, Math.min(consoleWidth - 1, n.getInteger("ConsoleIndex")));
        consoleDepth = block() != null && block().kind.equals("hadron_console") ? 1 : Math.max(1, Math.min(3, n.getInteger("ConsoleDepth")));
        consoleRow = Math.max(0, Math.min(consoleDepth - 1, n.getInteger("ConsoleRow")));
        active = n.getBoolean("Active");
        mount=n.hasKey("MountFace")?EnumFacing.getFront(Math.max(0,Math.min(5,n.getInteger("MountFace")))):EnumFacing.UP;
        channels = ChannelData.read(n, 0);
        redstoneMode = n.hasKey("RedstoneMode") ? Math.max(DISABLED, Math.min(REDSTONE_OFF, n.getInteger("RedstoneMode"))) : DISABLED;
        manualOn = n.hasKey("ManualOn") ? n.getBoolean("ManualOn") : !n.hasKey("Active") || active;
        firstTick = true;
    }
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    public void onDataPacket(NetworkManager manager, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
    public AxisAlignedBB getRenderBoundingBox() {
        if (block() == null) return new AxisAlignedBB(pos);
        AxisAlignedBB result = new AxisAlignedBB(pos);
        for (AxisAlignedBB box : ShipSystemMesh.boxes(model(), facing(), mount, pos)) result = result.union(box);
        return result;
    }
}
