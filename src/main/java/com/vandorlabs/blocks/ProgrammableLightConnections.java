package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityProgrammableLight;
import com.vandorlabs.redstone.SignalUpdateBatch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.Set;

/** Adjacent light members shared by rendering and manual switching. */
public final class ProgrammableLightConnections {
    private ProgrammableLightConnections() { }
    private static final ThreadLocal<java.util.Map<World, PendingRefresh>> PENDING =
            ThreadLocal.withInitial(java.util.IdentityHashMap::new);

    private static final class PendingRefresh implements Runnable {
        final World world;
        final Set<BlockPos> positions = new LinkedHashSet<>();
        PendingRefresh(World world) { this.world=world; }
        @Override public void run() {
            java.util.Map<World,PendingRefresh> pending=PENDING.get();
            pending.remove(world);
            if (pending.isEmpty()) PENDING.remove();
            refreshAround(world,positions);
        }
    }

    public static Set<BlockPos> members(TileEntityProgrammableLight first,
            IBlockState state) {
        return members(first, state, true);
    }

    private static Set<BlockPos> members(TileEntityProgrammableLight first,
            IBlockState state, boolean matchOn) {
        World world = first.getWorld();
        if (!first.isJoin()) {
            Set<BlockPos> single = new LinkedHashSet<>();
            single.add(first.getPos());
            return single;
        }
        EnumFacing facing = state.getValue(BlockAnimatedScreenSelector.FACING);
        Set<BlockPos> members = LoadedPlaneConnections.collect(first.getPos(), PanelPlane.of(facing),
                world::isBlockLoaded, next -> eligible(world, next, facing, first, matchOn));
        if (members.size() >= LoadedPlaneConnections.LIMIT) {
            members.clear();
            members.add(first.getPos());
        }
        return members;
    }

    /** Reconcile only loaded assemblies touched by a signal or topology change. */
    public static void refreshAround(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null) return;
        if (SignalUpdateBatch.isActive()) {
            java.util.Map<World,PendingRefresh> pending=PENDING.get();
            PendingRefresh refresh=pending.get(world);
            if (refresh==null) {
                refresh=new PendingRefresh(world);
                pending.put(world,refresh);
                SignalUpdateBatch.afterSignals(refresh,refresh);
            }
            refresh.positions.add(pos.toImmutable());
            return;
        }
        refreshAround(world,java.util.Collections.singleton(pos));
    }

    private static void refreshAround(World world, Set<BlockPos> positions) {
        Set<BlockPos> visited = new LinkedHashSet<>();
        Set<BlockPos> seeds = new LinkedHashSet<>();
        for (BlockPos pos:positions) {
            seeds.add(pos);
            for (EnumFacing side : EnumFacing.values()) seeds.add(pos.offset(side));
        }
        for (BlockPos seed : seeds) {
            if (visited.contains(seed) || !world.isBlockLoaded(seed)) continue;
            TileEntity raw = world.getTileEntity(seed);
            if (!(raw instanceof TileEntityProgrammableLight) || !((TileEntityProgrammableLight) raw).isAvailableForJoining()
                    || world.getBlockState(seed).getBlock() != ModBlocks.PROGRAMMABLE_LIGHT) continue;
            Set<BlockPos> group = members((TileEntityProgrammableLight) raw,
                    world.getBlockState(seed), false);
            visited.addAll(group);
            boolean powered = false;
            for (BlockPos member : group)
                powered |= ((TileEntityProgrammableLight) world.getTileEntity(member)).hasDirectTriggerPower();
            for (BlockPos member : group)
                ((TileEntityProgrammableLight) world.getTileEntity(member)).setJoinedTriggerPower(powered);
        }
    }

    private static boolean eligible(World world, BlockPos pos, EnumFacing facing,
            TileEntityProgrammableLight first, boolean matchOn) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != ModBlocks.PROGRAMMABLE_LIGHT
                || state.getValue(BlockAnimatedScreenSelector.FACING) != facing) return false;
        TileEntity raw = world.getTileEntity(pos);
        if (!(raw instanceof TileEntityProgrammableLight)) return false;
        TileEntityProgrammableLight other = (TileEntityProgrammableLight) raw;
        return other.isAvailableForJoining() && other.isJoin() && other.getTexture() == first.getTexture()
                && (!matchOn || other.isOn() == first.isOn());
    }
}
