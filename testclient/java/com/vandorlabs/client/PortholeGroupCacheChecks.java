package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Lifecycle, exact aperture parity and idle-work checks against the released tick cache. */
public final class PortholeGroupCacheChecks {
    private static BlockProgrammableWall flat;
    private static final class World extends NonRenderingChecks.MemoryWorld {
        int tileReads;
        World() { super(true); }
        @Override public TileEntity getTileEntity(BlockPos pos) { tileReads++; return super.getTileEntity(pos); }
        void resetReads() { stateReads = tileReads = 0; }
    }
    private static void need(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    private static TileEntityAnimatedScreenSelector tile(net.minecraft.world.World world, BlockPos pos, int mode, int shape) {
        TileEntityAnimatedScreenSelector tile = new TileEntityAnimatedScreenSelector();
        tile.setJoinPortholes(true); tile.setDiagonalGeometry(mode, 0); tile.setPortholeShape(shape); tile.setWorld(world); tile.setPos(pos); return tile;
    }
    private static TileEntityAnimatedScreenSelector put(World world, BlockPos pos, IBlockState state, int mode, int shape) {
        TileEntityAnimatedScreenSelector tile = tile(world, pos, mode, shape); world.states.put(pos, state); world.tiles.put(pos, tile); return tile;
    }
    private static TEAnimatedScreenSelector.PortholeGroup group(TileEntityAnimatedScreenSelector tile, IBlockState state) { return TEAnimatedScreenSelector.portholeGroup(tile, state); }
    private static void arrays(List<double[]> a, List<double[]> b) {
        need(a.size() == b.size(), "aperture record count changed");
        for (int i = 0; i < a.size(); i++) need(Arrays.equals(a.get(i), b.get(i)), "aperture positions changed");
    }
    private static void parity(TileEntityAnimatedScreenSelector tile, IBlockState state) {
        TEAnimatedScreenSelector.PortholeGroup before = ReferencePortholeGroups.portholeGroup(tile, state), after = group(tile, state);
        need(before.minAxis == after.minAxis && before.minY == after.minY && before.columns == after.columns && before.rows == after.rows
                && before.right == after.right && before.up == after.up && before.shape == after.shape && before.extraBorder == after.extraBorder, "partition descriptor changed");
        EnumFacing right = state.getValue(BlockProgrammableWall.FACING).rotateY();
        EnumFacing up = ((BlockProgrammableWall)state.getBlock()).isDiagonalShape() && tile.isDiagonalHalfHeight() ? state.getValue(BlockProgrammableWall.FACING).getOpposite() : EnumFacing.UP;
        PortholeHex.Slice a = before.slice(tile.getPos(), right, up), b = after.slice(tile.getPos(), right, up);
        arrays(a.polygon, b.polygon); arrays(a.frameQuads, b.frameQuads); arrays(a.hexEdges, b.hexEdges); arrays(a.glassQuads, b.glassQuads);
    }
    private static void close(World world) { PortholeGroupCache.unload(world); ReferencePortholeGroups.clear(); }
    private static void geometry() {
        BlockProgrammableWall[] blocks = {flat, new BlockProgrammablePortholeBlock(), new BlockProgrammableWall("porthole_cache_diagonal", BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE)};
        int cases = 0;
        for (BlockProgrammableWall block : blocks) for (EnumFacing facing : EnumFacing.HORIZONTALS) for (boolean inverted : new boolean[]{false, true})
            for (int mode = 0; mode < (block.isDiagonalShape() ? 3 : 1); mode++) for (int shape = 0; shape < 4; shape++) for (int holes = 0; holes < 3; holes++) {
                World world = new World(); List<TileEntityAnimatedScreenSelector> tiles = new ArrayList<>();
                try {
                    IBlockState state = block.getDefaultState().withProperty(BlockProgrammableWall.FACING, facing).withProperty(BlockProgrammableWall.INVERTED, inverted);
                    BlockPos root = new BlockPos(8, 80, 8); EnumFacing right = facing.rotateY(), up = mode == 2 ? facing.getOpposite() : EnumFacing.UP;
                    for (int y = 0; y < 3; y++) for (int x = 0; x < 4; x++) {
                        if (holes == 1 && x == 1 && y == 1 || holes == 2 && x + y > 3) continue;
                        tiles.add(put(world, root.offset(right, x).offset(up, y), state, mode, shape));
                    }
                    for (TileEntityAnimatedScreenSelector tile : tiles) { parity(tile, state); cases++; }
                    List<TEAnimatedScreenSelector.PortholeGroup> previous = new ArrayList<>();
                    for (TileEntityAnimatedScreenSelector tile : tiles) previous.add(group(tile, state));
                    world.time += 20; world.resetReads();
                    for (int i = 0; i < tiles.size(); i++) need(group(tiles.get(i), state) == previous.get(i), "quiet tick changed group");
                    need(world.stateReads == 0 && world.tileReads == 0, "quiet geometry matrix queried world");
                } finally { close(world); }
            }
        System.out.println("porthole-group-cache exact geometry PASS members=" + cases + " thin/full/diagonal four shapes, holes, facings and inversion");
    }
    private static void idle() {
        World world = new World(), other = new World(); IBlockState state = flat.getDefaultState(); List<TileEntityAnimatedScreenSelector> panes = new ArrayList<>();
        try {
            for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) panes.add(put(world, new BlockPos(x, 64 + y, 8), state, 0, 0));
            TileEntityAnimatedScreenSelector second = put(other, new BlockPos(8, 80, 8), state, 0, 0);
            for (TileEntityAnimatedScreenSelector pane : panes) { group(pane, state); ReferencePortholeGroups.portholeGroup(pane, state); }
            TEAnimatedScreenSelector.PortholeGroup first = group(panes.get(0), state), secondGroup = group(second, state);
            int oldStates = 0, oldTiles = 0;
            for (int tick = 0; tick < 32; tick++) {
                world.time++; world.resetReads();
                for (TileEntityAnimatedScreenSelector pane : panes) ReferencePortholeGroups.portholeGroup(pane, state);
                oldStates += world.stateReads; oldTiles += world.tileReads; world.resetReads();
                for (TileEntityAnimatedScreenSelector pane : panes) group(pane, state);
                need(group(second, state) == secondGroup && group(panes.get(0), state) == first, "alternating worlds discarded warm topology");
                need(world.stateReads == 0 && world.tileReads == 0, "idle tick rescanned porthole group");
            }
            need(oldStates > 0 && oldTiles > 0, "old tick invalidation was not reproduced");
            System.out.println("porthole-group-cache idle panes=1024 ticks=32 old/new stateReads=" + oldStates + "/0 tileReads=" + oldTiles + "/0");
        } finally { close(world); close(other); }
    }
    private static void lifecycle() {
        World world = new World(); BlockPos pos = new BlockPos(8, 80, 8), next = pos.east(); IBlockState state = flat.getDefaultState();
        try {
            TileEntityAnimatedScreenSelector root = put(world, pos, state, 0, 0), neighbor = put(world, next, state, 0, 0);
            need(group(root, state).columns == 2, "joined lifecycle fixture");
            world.states.remove(next); world.tiles.remove(next); neighbor.invalidate();
            need(group(root, state).columns == 1, "silent same-block tile invalidation retained group");
            neighbor = tile(world, next, 0, 0); group(root, state); world.states.put(next, state); world.tiles.put(next, neighbor);
            world.tileLoadGuard = true; world.resetReads(); neighbor.onLoad(); world.tileLoadGuard = false;
            need(world.stateReads == 0 && world.tileReads == 0, "local lifecycle notification queried world");
            need(group(root, state).columns == 2, "tile onLoad retained missing member");
            TEAnimatedScreenSelector.PortholeGroup old = group(root, state); neighbor.onChunkUnload(); need(group(root, state) != old, "tile unload retained topology");
            NBTTagCompound data = neighbor.writeToNBT(new NBTTagCompound()); data.setBoolean("JoinPortholes", false); neighbor.readFromNBT(data);
            need(group(root, state).columns == 1, "NBT join edit retained group"); neighbor.setJoinPortholes(true);
            need(group(root, state).columns == 2, "join setting restore retained group"); neighbor.setPortholeShape(PortholeHex.ROUND);
            need(group(root, state).columns == 1, "shape setting retained group");
            old = group(root, state); world.notifyBlockUpdate(new BlockPos(128, 80, 128), Blocks.AIR.getDefaultState(), Blocks.AIR.getDefaultState(), 3);
            need(group(root, state) == old, "distant block event discarded topology"); world.markBlockRangeForRenderUpdate(128, 80, 128, 129, 81, 129);
            need(group(root, state) == old, "distant range event discarded topology"); world.notifyBlockUpdate(pos,state,state,3);
            need(group(root,state)!=old,"local block event not invalidated");old=group(root,state);world.markBlockRangeForRenderUpdate(pos,pos);
            need(group(root,state)!=old,"local range event not invalidated");old=group(root,state);PortholeGroupCache.clear();
            need(!PortholeGroupCache.contains(world) && group(root, state) != old, "resource-reset cache release");
            GroupCacheLifetimeChecks.run();
        } finally { world.tileLoadGuard = false; close(world); }
        World edge = new World(); pos = new BlockPos(15, 80, 8);
        try {
            TileEntityAnimatedScreenSelector root = put(edge, pos, state, 0, 0); put(edge, pos.east(), state, 0, 0); edge.chunkLimit = true;
            need(group(root, state).columns == 1, "loaded-boundary fixture"); edge.chunkLimit = false;
            net.minecraft.world.chunk.Chunk chunk = edge.getChunkFromChunkCoords(1, 0); DiagonalWallMeshCache.Events events = new DiagonalWallMeshCache.Events();
            events.chunkLoaded(new net.minecraftforge.event.world.ChunkEvent.Load(chunk)); need(group(root, state).columns == 2, "chunk load retained missing member");
            edge.chunkLimit = true; events.chunkUnloaded(new net.minecraftforge.event.world.ChunkEvent.Unload(chunk)); need(group(root, state).columns == 1, "chunk unload retained group");
        } finally { close(edge); }
    }
    private static void carried() {
        World parent = new World(); BlockPos pos = new BlockPos(8, 80, 8); IBlockState state = flat.getDefaultState();
        VehicleStructure initial = VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(pos, state, null), new VehicleStructure.Cell(pos.east(), state, null)));
        VehicleWorld view = new VehicleWorld(parent, initial);
        TileEntityAnimatedScreenSelector root = tile(view, pos, 0, 0), neighbor = tile(view, pos.east(), 0, 0); view.tiles.put(pos, root); view.tiles.put(pos.east(), neighbor);
        try {
            need(group(root, state).columns == 2, "carried fixture"); TEAnimatedScreenSelector.PortholeGroup old = group(root, state); view.tileRevision++;
            need(group(root, state) != old, "carried tile revision retained topology");
            view.structure = VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos, state, null)));
            need(group(root, state).columns == 1, "same-revision snapshot change retained removed member");
            old = group(root, state); view.origin = new BlockPos(1, 0, 0); need(group(root, state) != old, "carried origin guard");
        } finally { PortholeGroupCache.unload(view); }
    }
    private static void capacity() {
        World world = new World(); IBlockState state = flat.getDefaultState(); List<TileEntityAnimatedScreenSelector> stable = new ArrayList<>(), overflow = new ArrayList<>();
        try {
            for (int i = 0; i < PortholeGroupCache.MAX_POSITIONS; i++) stable.add(put(world, new BlockPos(i * 3, 80, 8), state, 0, 0));
            for (int i = 0; i < 64; i++) overflow.add(put(world, new BlockPos(i, 80, 64), state, 0, 0));
            for (TileEntityAnimatedScreenSelector tile : stable) group(tile, state);
            TEAnimatedScreenSelector.PortholeGroup first = group(stable.get(0), state), extra = group(overflow.get(0), state);
            world.resetReads();
            for (int round = 0; round < 4; round++) { world.time++; for (TileEntityAnimatedScreenSelector tile : overflow) need(group(tile, state) == extra, "overflow component was not retained whole"); }
            need(world.stateReads == 0 && world.tileReads == 0 && group(stable.get(0), state) == first, "over-budget scene thrashed admitted/component cache");
            need(PortholeGroupCache.entries(world) == PortholeGroupCache.MAX_POSITIONS + 64, "position budget accounting");
        } finally { close(world); }
        World limit = new World();
        try {
            TileEntityAnimatedScreenSelector first = null;
            for (int i = 0; i < LoadedPlaneConnections.LIMIT; i++) { TileEntityAnimatedScreenSelector tile = put(limit, new BlockPos(i, 80, 8), state, 0, 0); if (first == null) first = tile; }
            parity(first, state); TEAnimatedScreenSelector.PortholeGroup fallback = group(first, state); need(fallback.columns == 1 && fallback.rows == 1, "scan-limit fallback changed");
            limit.time++; limit.resetReads(); need(group(first, state) == fallback && limit.stateReads == 0 && limit.tileReads == 0, "scan-limit fallback rescanned every tick");
            need(PortholeGroupCache.entries(limit) <= PortholeGroupCache.MAX_POSITIONS + LoadedPlaneConnections.LIMIT, "cache limit exceeded");
        } finally { close(limit); }
    }
    public static void main(String[] args) throws Exception {
        java.lang.reflect.Field environment=net.minecraftforge.fml.relauncher.CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");environment.setAccessible(true);environment.setBoolean(null,true);
        net.minecraft.init.Bootstrap.register(); com.vandorlabs.VandorLabs.proxy = new com.vandorlabs.CommonProxy(){@Override public void portholeGeometryChanged(TileEntityAnimatedScreenSelector tile){TEAnimatedScreenSelector.portholeChanged(tile);}};
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class, new net.minecraft.util.ResourceLocation("vandorlabs", "porthole_group_cache_checks"));
        flat = new BlockProgrammableWall("porthole_cache_flat", BlockProgrammableWall.Shape.PORTHOLE);
        geometry(); idle(); lifecycle(); carried(); capacity();
        System.out.println("porthole-group-cache PASS geometry/idle/multiworld/block+range/lifecycle+NBT/settings/chunks/carried/resource-reset/world-unload/8192+overflow/scan-limit");
    }
    private PortholeGroupCacheChecks() { }
}
