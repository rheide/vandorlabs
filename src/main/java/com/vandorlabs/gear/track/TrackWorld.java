package com.vandorlabs.gear.track;

import com.vandorlabs.gear.track.core.TrackMounting;
import com.vandorlabs.gear.track.core.TrackTopology;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Bounded server discovery. Every world read is guarded; no chunk is forced to load. */
public final class TrackWorld {
    private static final EnumFacing[] SIDES = { EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST };

    private TrackWorld() { }

    public static boolean loaded(World world, BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() < world.getHeight() && world.isBlockLoaded(pos, false);
    }

    @Nullable
    public static TrackTile tile(World world, BlockPos pos) {
        if (!loaded(world, pos)) return null;
        TileEntity raw = world.getTileEntity(pos);
        return raw instanceof TrackTile ? (TrackTile) raw : null;
    }

    public static TrackTopology.Cell cell(BlockPos pos) { return new TrackTopology.Cell(pos.getX(), pos.getY(), pos.getZ()); }
    public static BlockPos pos(TrackTopology.Cell cell) { return new BlockPos(cell.x, cell.y, cell.z); }
    public static TrackTopology.Facing facing(EnumFacing facing) { return TrackTopology.Facing.valueOf(facing.name()); }

    public static void requestAround(World world, BlockPos changed) {
        if (world.isRemote) return;
        request(world, changed);
        for (EnumFacing side : EnumFacing.values()) request(world, changed.offset(side));
    }

    private static void request(World world, BlockPos pos) {
        TrackTile tile = tile(world, pos);
        if (tile != null) tile.requestRebuild();
    }

    public static final class Scan {
        public final TrackTopology.Assembly assembly;
        public final List<BlockPos> visited;
        public final String error;

        private Scan(TrackTopology.Assembly assembly, Set<BlockPos> visited, String error) {
            this.assembly = assembly;
            this.visited = Collections.unmodifiableList(new ArrayList<BlockPos>(visited));
            this.error = error;
        }

        public boolean valid() { return assembly != null; }
    }

    private static Scan failed(Set<BlockPos> visited, String error) { return new Scan(null, visited, error); }

    /** Public host-integration entry point. A caller receives a complete valid group or a failure. */
    public static Scan scan(World world, BlockPos start) {
        Set<BlockPos> visited = new LinkedHashSet<BlockPos>();
        if (!loaded(world, start)) return failed(visited, "The track chunk is unavailable.");
        IBlockState first = world.getBlockState(start);
        if (!(first.getBlock() instanceof TrackBlock)) return failed(visited, "No track at the requested position.");
        EnumFacing orientation = first.getValue(TrackBlock.FACING);
        TrackTopology.Style style = TrackBlock.style(world,start);
        ArrayDeque<BlockPos> queue = new ArrayDeque<BlockPos>();
        List<TrackTopology.Node> nodes = new ArrayList<TrackTopology.Node>();
        visited.add(start.toImmutable()); queue.add(start.toImmutable());
        int minX = start.getX(), maxX = minX, minZ = start.getZ(), maxZ = minZ;
        String unavailable = "";
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (tile(world, current) == null) unavailable = "A track tile is not ready.";
            nodes.add(new TrackTopology.Node(cell(current), style, facing(orientation)));
            for (EnumFacing side : SIDES) {
                BlockPos neighbor = current.offset(side);
                if (!loaded(world, neighbor)) {
                    unavailable = "The track and its immediate boundary must be loaded.";
                    continue;
                }
                IBlockState state = world.getBlockState(neighbor);
                // Different styles, opposite facings and perpendicular facings are separate components.
                if (!(state.getBlock() instanceof TrackBlock) || TrackBlock.style(world,neighbor)!=style || state.getValue(TrackBlock.FACING) != orientation) continue;
                if (!visited.add(neighbor)) continue;
                if (visited.size() > TrackTopology.MAX_TRACKS) return failed(visited, "More than 64 connected tracks; no partial assembly was accepted.");
                minX = Math.min(minX, neighbor.getX()); maxX = Math.max(maxX, neighbor.getX());
                minZ = Math.min(minZ, neighbor.getZ()); maxZ = Math.max(maxZ, neighbor.getZ());
                if ((long) maxX - minX + 1 > TrackTopology.MAX_HORIZONTAL_SPAN
                        || (long) maxZ - minZ + 1 > TrackTopology.MAX_HORIZONTAL_SPAN) {
                    return failed(visited, "The track footprint exceeds 16 blocks in one horizontal direction.");
                }
                queue.addLast(neighbor);
            }
        }
        if (!unavailable.isEmpty()) return failed(visited, unavailable);
        TrackTopology.Result result = TrackTopology.analyze(nodes);
        return result.isValid() ? new Scan(result.assembly(), visited, "") : failed(visited, result.error());
    }

    public static void rebuild(TrackTile initiatingTile) {
        World world = initiatingTile.getWorld();
        if (world == null || world.isRemote || initiatingTile.isInvalid()) return;
        Scan scan = scan(world, initiatingTile.getPos());
        Set<BlockPos> affected = previousMembers(world, scan.visited);
        affected.addAll(scan.visited);
        if (!scan.valid()) {
            // Clear previously cached groups too. A new65th cell cannot leave an old64-cell
            // group visually joined merely because discovery stopped at its safe bound.
            for (BlockPos old : affected) fallback(world, old, false, scan.error);
            initiatingTile.defer();
            return;
        }
        Set<BlockPos> members = new LinkedHashSet<BlockPos>();
        boolean groupSupported = false;
        for (TrackTopology.Part part : scan.assembly.parts()) {
            BlockPos member = pos(part.cell());
            members.add(member);
            groupSupported |= hasOverhead(world, member) || slabSides(world, member) != 0;
        }
        for (BlockPos old : affected) if (!members.contains(old)) fallback(world, old, true, "Topology changed.");
        BlockPos leader = pos(scan.assembly.leader());
        List<BlockPos> memberList = new ArrayList<BlockPos>(members);
        for (TrackTopology.Part part : scan.assembly.parts()) {
            BlockPos member = pos(part.cell());
            TrackTile tile = tile(world, member);
            if (tile == null) continue; // Discovery already checked; defensive against a host callback.
            TrackAppearance appearance = new TrackAppearance(part.beltMask(),
                    mount(world, member, member.equals(leader) && !groupSupported), slabSides(world, member),
                    (part.chassisMask() & ~part.beltMask()) & (TrackTopology.LEFT | TrackTopology.RIGHT),lowerSlabSides(world,member));
            tile.install(appearance, leader, member.equals(leader) ? memberList : Collections.<BlockPos>emptyList(), true, "");
        }
    }

    /** Invalidate prior component caches when breaking a cell, without removing any blocks. */
    public static void releaseCachedGroup(World world, TrackTile source) {
        if (world.isRemote) return;
        Set<BlockPos> affected = previousMembers(world, Collections.singletonList(source.getPos()));
        affected.add(source.getPos());
        for (BlockPos member : affected) fallback(world, member, true, "A track was removed.");
    }

    private static Set<BlockPos> previousMembers(World world, List<BlockPos> seeds) {
        Set<BlockPos> owners = new HashSet<BlockPos>();
        Set<BlockPos> members = new LinkedHashSet<BlockPos>();
        for (BlockPos seed : seeds) {
            TrackTile tile = tile(world, seed);
            if (tile != null && tile.leader() != null) owners.add(tile.leader());
        }
        for (BlockPos owner : owners) {
            TrackTile tile = tile(world, owner);
            if (tile != null) members.addAll(tile.trackMembers());
        }
        return members;
    }

    private static void fallback(World world, BlockPos pos, boolean retrySoon, String error) {
        TrackTile tile = tile(world, pos);
        if (tile == null) return;
        TrackAppearance appearance = new TrackAppearance(0, mount(world, pos, slabSides(world, pos) == 0), slabSides(world, pos), 0,lowerSlabSides(world,pos));
        tile.install(appearance, pos, Collections.singletonList(pos), false, error);
        if (retrySoon) tile.requestRebuild();
    }

    private static boolean hasOverhead(World world, BlockPos pos) {
        BlockPos above = pos.up();
        return loaded(world, above) && TrackMounting.acceptsOverhead(world.isAirBlock(above));
    }

    private static TrackAppearance.Mount mount(World world, BlockPos pos, boolean freeSocket) {
        if (!hasOverhead(world, pos)) return freeSocket ? TrackAppearance.Mount.SOCKET : TrackAppearance.Mount.NONE;
        IBlockState above = world.getBlockState(pos.up());
        ResourceLocation id = above.getBlock().getRegistryName();
        if (id != null && "vandorlabs".equals(id.getResourceDomain())
                && ("gear_extender".equals(id.getResourcePath()) || "gear_swing_arm".equals(id.getResourcePath()))) {
            // This selects the attachment geometry only. The older gear controller still
            // needs the documented host adapter before it can discover/animate track feet.
            return TrackAppearance.Mount.SOCKET;
        }
        return isTopSlab(above) ? TrackAppearance.Mount.TOP_HIGH : TrackAppearance.Mount.TOP;
    }

    public static boolean isTopSlab(IBlockState state) {
        if (!(state.getBlock() instanceof BlockSlab)) return false;
        BlockSlab slab = (BlockSlab) state.getBlock();
        return TrackMounting.acceptsSlabSide(true, slab.isDouble(),
                !slab.isDouble() && state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP);
    }

    public static int lowerSlabSides(World world,BlockPos pos){int result=0;for(int side=0;side<SIDES.length;side++){BlockPos p=pos.offset(SIDES[side]);if(!loaded(world,p))continue;IBlockState s=world.getBlockState(p);if(s.getBlock() instanceof BlockSlab&&!((BlockSlab)s.getBlock()).isDouble()&&s.getValue(BlockSlab.HALF)==BlockSlab.EnumBlockHalf.BOTTOM)result|=1<<side;}return result;}

    public static int slabSides(World world, BlockPos pos) {
        int result = 0;
        for (int side = 0; side < SIDES.length; side++) {
            BlockPos neighbor = pos.offset(SIDES[side]);
            if (loaded(world, neighbor) && world.getBlockState(neighbor).getBlock() instanceof BlockSlab) result |= 1 << side;
        }
        return result;
    }
}
