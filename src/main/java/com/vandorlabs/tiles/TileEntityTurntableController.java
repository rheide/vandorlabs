package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockTurntable;
import com.vandorlabs.blocks.BlockTurntableController;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.redstone.ChannelData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.turntable.TurntableGeometry;
import com.vandorlabs.turntable.TurntableSnapshot;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Brain of a Turntable. It scans the square of Turntable blocks in front of
 * the controller, captures the volume they point at, and turns that volume
 * about the platform axis while a trigger is asserted. Turning it off restores
 * the captured blocks in place.
 */
public class TileEntityTurntableController extends TileEntity implements RedstoneChannelMember {
    public static final int TRIGGER_DISABLED = 0;
    public static final int TRIGGER_POWERED = 1;
    public static final int TRIGGER_UNPOWERED = 2;

    private ChannelList channels = ChannelList.EMPTY;
    private int redstoneChannel;
    private boolean channelSignal;
    private boolean latched, signalKnown, lifecycleDetached;

    public int trigger = TRIGGER_POWERED;
    public int height = 4;
    public double speed = 90;
    public int rotationDirection = TurntableGeometry.CLOCKWISE;
    public String status = "Idle";
    public boolean error;
    public int clientUpdates;
    public UUID owner;

    private int axis = TurntableGeometry.AXIS_Y;
    private EnumFacing payloadFacing = EnumFacing.UP;
    private final Set<BlockPos> platform = new LinkedHashSet<>();
    private int size;
    private double pivotX, pivotY, pivotZ;
    private boolean valid;

    private final List<TurntableSnapshot.Cell> payload = new ArrayList<>();
    private boolean captured, active;
    private double startAngle;
    private long startTick;
    private boolean needsLoadCheck;

    // Client-side render snapshot, refreshed from the network.
    private TurntableSnapshot clientPayload;
    private AxisAlignedBB clientRenderBounds;

    public TileEntityTurntableController() { }

    // ---------------------------------------------------------------- channels

    @Override public TileEntity channelTile() { return this; }
    @Override public int getRedstoneChannel() { return redstoneChannel; }
    @Override public ChannelList getRedstoneChannels() { return channels; }
    @Override public int localSignalLevel(int channel) {
        return com.vandorlabs.redstone.LoadedRedstonePower.level(world, pos);
    }
    @Override public boolean hasLocalRedstoneSignal() {
        return com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world, pos);
    }
    @Override public void setChannelSignal(boolean powered) {
        if (!acceptsWorldCommand() || channelSignal == powered) return;
        channelSignal = powered;
        evaluateSignal(false);
    }
    @Override public void setRedstoneChannel(int value) { setRedstoneChannels(ChannelList.of(Math.max(0, value))); }
    @Override public void setRedstoneChannels(ChannelList next) {
        if (channels.equals(next)) return;
        ChannelList old = channels;
        channels = next;
        redstoneChannel = next.first();
        markDirty();
        RedstoneChannels.channelChanged(this, old);
        if (world != null && !world.isRemote) evaluateSignal(true);
    }

    private boolean acceptsWorldCommand() {
        return world != null && !world.isRemote && !isInvalid() && !lifecycleDetached
                && !com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)
                && !(world instanceof com.vandorlabs.vehicle.VehicleWorld)
                && world.getTileEntity(pos) == this;
    }

    // ------------------------------------------------------------------ setup

    @Override public boolean shouldRefresh(World world, BlockPos pos, IBlockState before, IBlockState after) {
        return before.getBlock() != after.getBlock();
    }

    public boolean usable(EntityPlayer player) {
        return world != null && world.getTileEntity(pos) == this && player != null
                && player.getDistanceSq(pos) <= 64;
    }

    public int axis() { return axis; }
    public int size() { return size; }
    public boolean isValidTurntable() { return valid; }
    public boolean isActive() { return active; }
    public boolean isCaptured() { return captured; }
    public int payloadSize() { return payload.size(); }
    public double pivotX() { return pivotX; }
    public double pivotY() { return pivotY; }
    public double pivotZ() { return pivotZ; }
    public double startAngle() { return startAngle; }
    public long startTick() { return startTick; }
    /** The captured volume as an immutable snapshot, ready for the wire. */
    public TurntableSnapshot captureSnapshot() {
        return payload.isEmpty() ? null : new TurntableSnapshot(new ArrayList<>(payload));
    }
    public TurntableSnapshot clientPayload() { return clientPayload; }
    public void setClientPayload(TurntableSnapshot snapshot) {
        clientPayload = snapshot;
        clientRenderBounds = null;
    }

    public double angle(float partial) {
        if (!active) return TurntableGeometry.normalize(startAngle);
        return TurntableGeometry.angle(startAngle, world == null ? 0 : world.getTotalWorldTime(),
                startTick, partial, speed, rotationDirection);
    }

    // ------------------------------------------------------------ scan/capture

    /** Validate the platform square and report the result without moving blocks. */
    public boolean scan() {
        error = false;
        if (!acceptsWorldCommand()) return false;
        String problem = resolvePlatform();
        if (problem != null) return fail(problem);
        // Count without mutating so the dialog can preview the payload.
        List<BlockPos> preview = new ArrayList<>();
        problem = collectPayload(preview);
        if (problem != null) return fail(problem);
        status = "Turntable " + size + "x" + size + " ready; " + preview.size() + " blocks";
        sync();
        return true;
    }

    /** Flood-fill the connected turntable blocks in the plane in front of the controller. */
    private String resolvePlatform() {
        platform.clear();
        valid = false;
        IBlockState state = world.getBlockState(pos);
        EnumFacing facing = state.getBlock() instanceof BlockTurntableController
                ? state.getValue(BlockVandorDirectional.FACING) : EnumFacing.NORTH;
        BlockPos front = pos.offset(facing);
        if (!world.isBlockLoaded(front)) return "The turntable is in an unloaded chunk.";
        IBlockState startState = world.getBlockState(front);
        if (!(startState.getBlock() instanceof BlockTurntable))
            return "Place Turntable blocks in front of the controller.";
        EnumFacing payload = startState.getValue(BlockTurntable.FACING);
        int resolvedAxis = axisIndex(payload.getAxis());
        this.payloadFacing = payload;
        this.axis = resolvedAxis;

        int[] plane = TurntableGeometry.planeAxes(resolvedAxis);
        int startC = axisCoordinate(front, resolvedAxis);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        queue.add(front.toImmutable());
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!seen.add(current)) continue;
            if (!world.isBlockLoaded(current)) return "The turntable crosses an unloaded chunk.";
            IBlockState block = world.getBlockState(current);
            // Anything that is not a Turntable block is a boundary: air, the
            // controller itself, or terrain around the platform. Only matching
            // Turntable blocks join the platform.
            if (!(block.getBlock() instanceof BlockTurntable)) continue;
            if (block.getValue(BlockTurntable.FACING) != payload)
                return "Expected a matching Turntable block at " + describe(current) + ".";
            if (axisCoordinate(current, resolvedAxis) != startC)
                return "The turntable is not flat.";
            platform.add(current);
            if (platform.size() > TurntableGeometry.MAX_PLATFORM)
                return "The turntable is larger than 16x16.";
            queue.add(current.offset(sideFacing(plane[0], 1)));
            queue.add(current.offset(sideFacing(plane[0], -1)));
            queue.add(current.offset(sideFacing(plane[1], 1)));
            queue.add(current.offset(sideFacing(plane[1], -1)));
        }

        int[] coords = planeBounds(plane);
        int width = coords[2] - coords[0] + 1, depth = coords[3] - coords[1] + 1;
        if (!TurntableGeometry.isSquare(width, depth))
            return "The turntable must be a square; it is " + width + "x" + depth + ".";
        if (!TurntableGeometry.isFilledSquare(platform.size(), width))
            return "The turntable must be a filled " + width + "x" + depth + " square.";
        size = width;
        double centerA = (coords[0] + coords[2] + 1) / 2.0, centerB = (coords[1] + coords[3] + 1) / 2.0;
        double centerC = startC + 0.5;
        double[] pivot = planePoint(resolvedAxis, centerA, centerB, centerC);
        pivotX = pivot[0];
        pivotY = pivot[1];
        pivotZ = pivot[2];
        valid = true;
        return null;
    }

    /** Collect the non-air blocks along the payload direction, up to {@link #height}. */
    private String collectPayload(List<BlockPos> out) {
        out.clear();
        for (BlockPos cell : platform) {
            for (int step = 1; step <= height; step++) {
                BlockPos target = cell.offset(payloadFacing, step);
                if (!world.isBlockLoaded(target)) return "The rotating volume crosses an unloaded chunk.";
                if (target.equals(pos)) return "Move the controller out of the rotating volume.";
                if (platform.contains(target)) continue;
                IBlockState state = world.getBlockState(target);
                if (state.getBlock().isAir(state, world, target)) continue;
                if (state.getMaterial().isLiquid()) return "The rotating volume contains a liquid.";
                Block block = state.getBlock();
                if (block == net.minecraft.init.Blocks.BEDROCK) return "The rotating volume contains bedrock.";
                if (state.getBlockHardness(world, target) < 0)
                    return "The rotating volume contains an unbreakable block.";
                if (out.size() >= TurntableGeometry.MAX_PAYLOAD)
                    return "The rotating volume holds more than " + TurntableGeometry.MAX_PAYLOAD + " blocks.";
                out.add(target.toImmutable());
            }
        }
        return null;
    }

    /** Remove the payload from the world and keep it in the journal. */
    private boolean capture() {
        String problem = resolvePlatform();
        if (problem != null) return fail(problem);
        List<BlockPos> positions = new ArrayList<>();
        problem = collectPayload(positions);
        if (problem != null) return fail(problem);
        payload.clear();
        for (BlockPos target : positions) {
            IBlockState state = world.getBlockState(target);
            TileEntity tile = world.getTileEntity(target);
            NBTTagCompound data = tile == null ? null : tile.writeToNBT(new NBTTagCompound());
            payload.add(new TurntableSnapshot.Cell(target, state, data));
        }
        for (BlockPos target : positions) world.setBlockToAir(target);
        captured = true;
        return true;
    }

    /** Put the captured blocks back where they were. */
    private void restore() {
        int blocked = 0;
        for (TurntableSnapshot.Cell cell : payload) {
            BlockPos target = cell.pos;
            if (!world.isBlockLoaded(target)) { blocked++; continue; }
            IBlockState existing = world.getBlockState(target);
            if (!existing.getBlock().isAir(existing, world, target) && existing != cell.state) {
                drop(cell, target);
                blocked++;
                continue;
            }
            world.setBlockState(target, cell.state, 3);
            if (cell.hasTileData()) {
                TileEntity tile = world.getTileEntity(target);
                if (tile != null) {
                    NBTTagCompound data = cell.tileData();
                    data.setInteger("x", target.getX());
                    data.setInteger("y", target.getY());
                    data.setInteger("z", target.getZ());
                    tile.readFromNBT(data);
                    tile.markDirty();
                    world.notifyBlockUpdate(target, cell.state, cell.state, 3);
                }
            }
        }
        payload.clear();
        captured = false;
        if (blocked > 0) status = "Restored; " + blocked + " blocked cells were dropped";
    }

    private void drop(TurntableSnapshot.Cell cell, BlockPos target) {
        ItemStack stack = new ItemStack(cell.state.getBlock(), 1,
                cell.state.getBlock().damageDropped(cell.state));
        if (stack.isEmpty()) return;
        world.spawnEntity(new EntityItem(world, target.getX() + .5, target.getY() + .5,
                target.getZ() + .5, stack));
    }

    /** Restore everything owned by this controller. Used on removal and load. */
    public void recover(boolean dropPayload) {
        if (world == null || world.isRemote || world instanceof com.vandorlabs.vehicle.VehicleWorld) return;
        if (captured) {
            if (dropPayload) {
                for (TurntableSnapshot.Cell cell : payload) drop(cell, cell.pos);
                payload.clear();
                captured = false;
            } else {
                restore();
            }
        }
        active = false;
        publishParts();
    }

    // ------------------------------------------------------------- redstone

    public void updatePower() {
        if (!acceptsWorldCommand()) return;
        RedstoneChannels.inputChanged(this);
        evaluateSignal(false);
    }

    private void evaluateSignal(boolean force) {
        if (!acceptsWorldCommand()) return;
        boolean signal = hasLocalRedstoneSignal() || channelSignal;
        boolean desired = trigger != TRIGGER_DISABLED
                && signal == (trigger == TRIGGER_POWERED);
        if (!force && signalKnown && desired == latched) return;
        signalKnown = true;
        latched = desired;
        request(desired);
    }

    /** Public so the dialog and the load check can drive the platform. */
    public boolean request(boolean deploy) {
        if (!acceptsWorldCommand()) return false;
        if (deploy) {
            if (!captured && !capture()) { publishParts(); return false; }
            if (!valid) return fail("The turntable is not a valid square.");
            startAngle = TurntableGeometry.normalize(startAngle);
            startTick = world.getTotalWorldTime();
            active = true;
            error = false;
            status = "Rotating at " + (int) speed + "\u00B0/s";
            RedstoneChannels.register(this);
        } else {
            if (captured) restore();
            active = false;
            error = false;
            if (!status.startsWith("Restored")) status = "Idle";
        }
        sync();
        publishParts();
        return true;
    }

    private boolean fail(String message) {
        error = true;
        status = message;
        active = false;
        sync();
        return false;
    }

    // ------------------------------------------------------------------ parts

    private void publishParts() {
        if (world == null || world.isRemote) return;
        for (BlockPos part : platform) {
            if (!world.isBlockLoaded(part)) continue;
            TileEntity tile = world.getTileEntity(part);
            if (tile instanceof TileEntityTurntable) {
                ((TileEntityTurntable) tile).configure(pos, active, startAngle, startTick,
                        speed, rotationDirection, axis);
                world.notifyBlockUpdate(part, world.getBlockState(part), world.getBlockState(part), 3);
            }
        }
    }

    public void onPartRemoved(BlockPos part) {
        if (!acceptsWorldCommand()) return;
        if (active || captured) {
            recover(true);
            error = true;
            status = "A Turntable block was removed";
        }
        platform.remove(part);
        valid = false;
        sync();
    }

    // --------------------------------------------------------------- lifecycle

    @Override public void onLoad() {
        lifecycleDetached = false;
        super.onLoad();
        if (world != null && !world.isRemote) {
            DeferredTileLoad.schedule(this, () -> RedstoneChannels.register(this));
            needsLoadCheck = true;
            world.scheduleUpdate(pos, world.getBlockState(pos).getBlock(), 1);
        }
    }

    @Override public void invalidate() {
        lifecycleDetached = true;
        RedstoneChannels.unregister(this);
        super.invalidate();
    }

    @Override public void onChunkUnload() {
        lifecycleDetached = true;
        RedstoneChannels.unregister(this);
        super.onChunkUnload();
    }

    public void scheduledTick() {
        if (!acceptsWorldCommand()) return;
        if (needsLoadCheck) {
            needsLoadCheck = false;
            if (captured && !active) restore();
            evaluateSignal(true);
        }
    }

    // ---------------------------------------------------------------- syncing

    private void sync() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            com.vandorlabs.turntable.TurntableNetwork.broadcast(this);
        }
    }

    private NBTTagCompound clientTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("Pos", pos.toLong());
        tag.setInteger("Trigger", trigger);
        tag.setInteger("Height", height);
        tag.setDouble("Speed", speed);
        tag.setInteger("Direction", rotationDirection);
        tag.setInteger("Axis", axis);
        tag.setInteger("PayloadFacing", payloadFacing.getIndex());
        tag.setBoolean("Active", active);
        tag.setBoolean("Captured", captured);
        tag.setBoolean("Valid", valid);
        tag.setBoolean("Error", error);
        tag.setInteger("Size", size);
        tag.setDouble("PivotX", pivotX);
        tag.setDouble("PivotY", pivotY);
        tag.setDouble("PivotZ", pivotZ);
        tag.setDouble("StartAngle", startAngle);
        tag.setLong("StartTick", startTick);
        tag.setString("Status", status);
        tag.setInteger("RedstoneChannel", redstoneChannel);
        ChannelData.write(tag, channels);
        return tag;
    }

    private void readClientTag(NBTTagCompound tag) {
        trigger = tag.getInteger("Trigger");
        height = tag.getInteger("Height");
        speed = tag.getDouble("Speed");
        rotationDirection = tag.getInteger("Direction");
        axis = tag.getInteger("Axis");
        payloadFacing = EnumFacing.getFront(tag.getInteger("PayloadFacing"));
        boolean wasActive = active;
        active = tag.getBoolean("Active");
        captured = tag.getBoolean("Captured");
        valid = tag.getBoolean("Valid");
        error = tag.getBoolean("Error");
        size = tag.getInteger("Size");
        pivotX = tag.getDouble("PivotX");
        pivotY = tag.getDouble("PivotY");
        pivotZ = tag.getDouble("PivotZ");
        startAngle = tag.getDouble("StartAngle");
        startTick = tag.getLong("StartTick");
        status = tag.getString("Status");
        redstoneChannel = tag.getInteger("RedstoneChannel");
        channels = ChannelData.read(tag, redstoneChannel);
        clientUpdates++;
        if (!active) { clientPayload = null; clientRenderBounds = null; }
        else if (!wasActive && clientPayload == null) com.vandorlabs.turntable.TurntableNetwork.request(pos);
    }

    @Override public NBTTagCompound getUpdateTag() { return clientTag(); }

    @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, clientTag());
    }

    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readClientTag(packet.getNbtCompound());
    }

    @Override public void handleUpdateTag(NBTTagCompound tag) { readClientTag(tag); }

    @Override public AxisAlignedBB getRenderBoundingBox() {
        if (clientPayload == null || clientPayload.isEmpty())
            return new AxisAlignedBB(pos).grow(0.5);
        if (clientRenderBounds == null) {
            AxisAlignedBB box = new AxisAlignedBB(pos);
            for (TurntableSnapshot.Cell cell : clientPayload.cells) box = box.union(new AxisAlignedBB(cell.pos));
            // The payload sweeps around the pivot, so allow for the full turned footprint.
            double radius = Math.max(Math.abs(pivotX - pos.getX()), Math.abs(pivotZ - pos.getZ()))
                    + Math.sqrt(2) * Math.max(1, size);
            box = box.expand(radius, 0, radius).union(new AxisAlignedBB(pivotX - 1, pivotY - 1, pivotZ - 1,
                    pivotX + 1, pivotY + 1, pivotZ + 1));
            clientRenderBounds = box;
        }
        return clientRenderBounds;
    }

    // ------------------------------------------------------------- disk codec

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("Trigger", trigger);
        tag.setInteger("Height", height);
        tag.setDouble("Speed", speed);
        tag.setInteger("Direction", rotationDirection);
        tag.setInteger("Axis", axis);
        tag.setInteger("PayloadFacing", payloadFacing.getIndex());
        tag.setBoolean("Active", active);
        tag.setDouble("StartAngle", startAngle);
        tag.setLong("StartTick", startTick);
        tag.setString("Status", status);
        tag.setInteger("RedstoneChannel", redstoneChannel);
        tag.setBoolean("Captured", captured);
        ChannelData.write(tag, channels);
        if (owner != null) tag.setString("Owner", owner.toString());
        if (captured) {
            NBTTagList list = new NBTTagList();
            for (TurntableSnapshot.Cell cell : payload) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setLong("Pos", cell.pos.toLong());
                entry.setTag("State", NBTUtil.writeBlockState(new NBTTagCompound(), cell.state));
                NBTTagCompound data = cell.tileData();
                if (data != null) entry.setTag("Tile", data);
                list.appendTag(entry);
            }
            tag.setTag("Payload", list);
        }
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        trigger = tag.getInteger("Trigger");
        height = TurntableGeometry.clampHeight(tag.hasKey("Height") ? tag.getInteger("Height") : 4);
        speed = TurntableGeometry.clampSpeed(tag.hasKey("Speed") ? tag.getDouble("Speed") : 90);
        rotationDirection = tag.getInteger("Direction") == TurntableGeometry.COUNTER_CLOCKWISE
                ? TurntableGeometry.COUNTER_CLOCKWISE : TurntableGeometry.CLOCKWISE;
        axis = tag.getInteger("Axis");
        if (!TurntableGeometry.validAxis(axis)) axis = TurntableGeometry.AXIS_Y;
        payloadFacing = EnumFacing.getFront(tag.getInteger("PayloadFacing"));
        if (payloadFacing.getAxis() == null) payloadFacing = EnumFacing.UP;
        active = tag.getBoolean("Active");
        startAngle = tag.getDouble("StartAngle");
        startTick = tag.getLong("StartTick");
        status = tag.getString("Status");
        redstoneChannel = tag.getInteger("RedstoneChannel");
        channels = ChannelData.read(tag, redstoneChannel);
        captured = tag.getBoolean("Captured");
        owner = tag.hasKey("Owner", 8) ? UUID.fromString(tag.getString("Owner")) : null;
        payload.clear();
        if (captured) {
            NBTTagList list = tag.getTagList("Payload", 10);
            for (int i = 0; i < list.tagCount() && i < TurntableGeometry.MAX_PAYLOAD; i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                IBlockState state = NBTUtil.readBlockState(entry.getCompoundTag("State"));
                payload.add(new TurntableSnapshot.Cell(BlockPos.fromLong(entry.getLong("Pos")), state,
                        entry.hasKey("Tile", 10) ? entry.getCompoundTag("Tile") : null));
            }
        }
        needsLoadCheck = true;
    }

    // ----------------------------------------------------------------- helpers

    private static int axisIndex(EnumFacing.Axis axis) {
        switch (axis) {
            case X: return TurntableGeometry.AXIS_X;
            case Y: return TurntableGeometry.AXIS_Y;
            default: return TurntableGeometry.AXIS_Z;
        }
    }

    private static int axisCoordinate(BlockPos p, int axis) {
        switch (axis) {
            case TurntableGeometry.AXIS_X: return p.getX();
            case TurntableGeometry.AXIS_Z: return p.getZ();
            default: return p.getY();
        }
    }

    private static EnumFacing sideFacing(int axis, int sign) {
        switch (axis) {
            case TurntableGeometry.AXIS_X: return sign > 0 ? EnumFacing.EAST : EnumFacing.WEST;
            case TurntableGeometry.AXIS_Z: return sign > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
            default: return sign > 0 ? EnumFacing.UP : EnumFacing.DOWN;
        }
    }

    private static double[] planePoint(int axis, double a, double b, double c) {
        switch (axis) {
            case TurntableGeometry.AXIS_X: return new double[] { c, a, b };
            case TurntableGeometry.AXIS_Z: return new double[] { a, b, c };
            default: return new double[] { a, c, b };
        }
    }

    private int[] planeBounds(int[] plane) {
        int minA = Integer.MAX_VALUE, minB = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE, maxB = Integer.MIN_VALUE;
        for (BlockPos cell : platform) {
            int a = axisCoordinate(cell, plane[0]), b = axisCoordinate(cell, plane[1]);
            minA = Math.min(minA, a); maxA = Math.max(maxA, a);
            minB = Math.min(minB, b); maxB = Math.max(maxB, b);
        }
        return new int[] { minA, minB, maxA, maxB };
    }

    private static String describe(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    /** The platform cells, used by the WorldEdit copy helpers and diagnostics. */
    public Set<BlockPos> platformCells() { return java.util.Collections.unmodifiableSet(platform); }
}
