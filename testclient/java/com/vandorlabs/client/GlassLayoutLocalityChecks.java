package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Artwork groups must survive distant edits but expire at every visited boundary. */
public final class GlassLayoutLocalityChecks {
    private static BlockProgrammableGlass glass;
    private static final class World extends NonRenderingChecks.MemoryWorld {
        int tileReads;
        World() { super(true); }
        @Override public TileEntity getTileEntity(BlockPos pos) { tileReads++; return super.getTileEntity(pos); }
        void resetReads() { stateReads = tileReads = 0; }
    }
    private static void need(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    private static TileEntityProgrammableGlass put(World world, BlockPos pos) {
        TileEntityProgrammableGlass tile = new TileEntityProgrammableGlass(); tile.setWorld(world); tile.setPos(pos);
        world.states.put(pos, glass.getDefaultState()); world.tiles.put(pos, tile); return tile;
    }
    private static void close(World world) { GlassSurfaceGroups.unload(world); ReferenceGlassSurfaceGroups.unload(world); }
    private static void parity(TileEntityProgrammableGlass tile) {
        need(GlassSurfaceGroups.layout(tile).key().equals(ReferenceGlassSurfaceGroups.layout(tile).key()), "artwork layout changed");
        need(GlassSurfaceGroups.frameMask(tile) == ReferenceGlassSurfaceGroups.frameMask(tile), "frame mask changed");
    }
    private static void distant() {
        World world = new World(); List<TileEntityProgrammableGlass> panes = new ArrayList<>();
        try {
            for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) panes.add(put(world, new BlockPos(x, 64 + y, 8)));
            for (TileEntityProgrammableGlass tile : panes) parity(tile);
            List<GlassSurfaceGroups.Layout> before = new ArrayList<>();
            for (TileEntityProgrammableGlass tile : panes) before.add(GlassSurfaceGroups.layout(tile));
            BlockPos far = new BlockPos(8192, 80, 8192); IBlockState air = Blocks.AIR.getDefaultState();
            for (int kind = 0; kind < 3; kind++) {
                if (kind == 0) world.notifyBlockUpdate(far, air, air, 3);
                if (kind == 1) world.markBlockRangeForRenderUpdate(8208, 96, 8208, 8192, 64, 8192);
                if (kind == 2) { GlassSurfaceGroups.invalidate(world, 8192, 0, 8192, 8207, 255, 8207); ReferenceGlassSurfaceGroups.invalidate(world); }
                world.resetReads();
                for (TileEntityProgrammableGlass tile : panes) { ReferenceGlassSurfaceGroups.layout(tile); ReferenceGlassSurfaceGroups.frameMask(tile); }
                int oldStates = world.stateReads, oldTiles = world.tileReads; world.resetReads();
                for (int i = 0; i < panes.size(); i++) {
                    need(GlassSurfaceGroups.layout(panes.get(i)) == before.get(i), "distant event discarded a layout");
                    GlassSurfaceGroups.frameMask(panes.get(i));
                }
                need(oldStates > 0 && oldTiles > 0, "reference invalidation was not exercised");
                need(world.stateReads == 0 && world.tileReads == 0, "distant event rescanned a layout or frame mask");
                System.out.println("glass-layout-locality panes=1024 event=" + kind + " old/new stateReads=" + oldStates + "/0 tileReads=" + oldTiles + "/0");
            }
            GlassMaterials.changed();
            need(GlassSurfaceGroups.layout(panes.get(0)) != before.get(0), "material revision must still expire layouts");
        } finally { close(world); }
    }
    private static void boundaries() {
        World world = new World(); BlockPos pos = new BlockPos(8, 80, 8), gap = pos.east();
        try {
            TileEntityProgrammableGlass root = put(world, pos); put(world, pos.east(2)); parity(root);
            need(GlassSurfaceGroups.layout(root).columns == 1, "missing bridge fixture");
            put(world, gap); world.notifyBlockUpdate(gap, Blocks.AIR.getDefaultState(), glass.getDefaultState(), 3); parity(root);
            need(GlassSurfaceGroups.layout(root).columns == 3, "rejected boundary cell did not admit new bridge");
            world.states.remove(gap); world.tiles.remove(gap); world.markBlockRangeForRenderUpdate(gap, gap); parity(root);
            need(GlassSurfaceGroups.layout(root).columns == 1, "removed bridge retained old group");
            put(world, gap); put(world, pos.up()); world.markBlockRangeForRenderUpdate(pos, pos.up().east()); parity(root);
            int missing = GlassSurfaceGroups.frameMask(root); BlockPos diagonal = pos.up().east();
            put(world, diagonal); world.notifyBlockUpdate(diagonal, Blocks.AIR.getDefaultState(), glass.getDefaultState(), 3); parity(root);
            need(GlassSurfaceGroups.frameMask(root) != missing, "frame-mask diagonal dependency was omitted");
            GlassSurfaceGroups.Layout saved = GlassSurfaceGroups.layout(root);
            GlassSurfaceGroups.invalidate(world, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
            need(GlassSurfaceGroups.layout(root) != saved, "reversed full range retained layout");
        } finally { close(world); }
        World edge = new World(); pos = new BlockPos(15, 80, 8);
        try {
            TileEntityProgrammableGlass root = put(edge, pos); put(edge, pos.east()); edge.chunkLimit = true;
            need(GlassSurfaceGroups.layout(root).columns == 1, "unloaded boundary fixture");
            edge.chunkLimit = false; net.minecraft.world.chunk.Chunk chunk = edge.getChunkFromChunkCoords(1, 0);
            DiagonalWallMeshCache.Events events = new DiagonalWallMeshCache.Events();
            events.chunkLoaded(new net.minecraftforge.event.world.ChunkEvent.Load(chunk));
            need(GlassSurfaceGroups.layout(root).columns == 2, "unloaded candidate was not tracked");
            edge.chunkLimit = true; events.chunkUnloaded(new net.minecraftforge.event.world.ChunkEvent.Unload(chunk));
            need(GlassSurfaceGroups.layout(root).columns == 1, "chunk unload retained joined artwork");
        } finally { close(edge); }
    }
    private static void diagonalBoundaries() {
        BlockProgrammableWall wall = new BlockProgrammableWall("glass_layout_diagonal_checks", BlockProgrammableWall.Shape.DIAGONAL);
        int cases = 0;
        for (EnumFacing facing : EnumFacing.HORIZONTALS) for (boolean inverted : new boolean[]{false, true}) for (int mode = 0; mode < 3; mode++) {
            World world = new World(); BlockPos pos = new BlockPos(8, 80, 8);
            try {
                IBlockState state = wall.getDefaultState().withProperty(BlockProgrammableWall.FACING, facing).withProperty(BlockProgrammableWall.INVERTED, inverted);
                TileEntityAnimatedScreenSelector root = new TileEntityAnimatedScreenSelector(); root.setDiagonalGeometry(mode, 0); root.setWorld(world); root.setPos(pos);
                world.states.put(pos, state); world.tiles.put(pos, root);
                for (boolean upper : new boolean[]{false, true}) for (BlockPos candidate : DiagonalPanelGeometry.rowCandidates(pos, state, mode, upper)) {
                    GlassSurfaceGroups.Layout old = GlassSurfaceGroups.mainLayout(root);
                    TileEntityAnimatedScreenSelector next = new TileEntityAnimatedScreenSelector(); next.setDiagonalGeometry(mode, 0); next.setWorld(world); next.setPos(candidate);
                    // Consume the global settings revision before changing the world;
                    // only the negative boundary event may invalidate the result below.
                    old = GlassSurfaceGroups.mainLayout(root);
                    world.states.put(candidate, state); world.tiles.put(candidate, next);
                    world.markBlockRangeForRenderUpdate(candidate, candidate);
                    GlassSurfaceGroups.Layout current = GlassSurfaceGroups.mainLayout(root);
                    need(current != old, "diagonal row candidate omitted");
                    need(current.key().equals(ReferenceGlassSurfaceGroups.mainLayout(root).key()), "diagonal main artwork changed");
                    world.states.remove(candidate); world.tiles.remove(candidate); world.markBlockRangeForRenderUpdate(candidate, candidate); cases++;
                }
            } finally { close(world); }
        }
        System.out.println("glass-layout-locality diagonal boundary PASS cases=" + cases);
    }
    private static void carriedAndLimit() {
        World parent = new World(); BlockPos pos = new BlockPos(8, 80, 8); IBlockState state = glass.getDefaultState();
        VehicleWorld carried = new VehicleWorld(parent, VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos, state, null))));
        TileEntityProgrammableGlass root = new TileEntityProgrammableGlass(); root.setWorld(carried); root.setPos(pos); carried.tiles.put(pos, root);
        try {
            GlassSurfaceGroups.Layout old = GlassSurfaceGroups.layout(root); carried.tileRevision++;
            need(GlassSurfaceGroups.layout(root) != old, "carried tile revision retained layout"); old = GlassSurfaceGroups.layout(root);
            carried.structure = VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos, state, null)));
            need(GlassSurfaceGroups.layout(root) != old, "same-revision snapshot replacement retained layout");
        } finally { GlassSurfaceGroups.unload(carried); }
        World large = new World();
        try {
            TileEntityProgrammableGlass first = null;
            for (int x = 0; x < LoadedPlaneConnections.LIMIT; x++) { TileEntityProgrammableGlass tile = put(large, new BlockPos(x, 80, 8)); if (first == null) first = tile; }
            GlassSurfaceGroups.Layout full = GlassSurfaceGroups.layout(first); need(full.columns == 1, "scan-cap fixture");
            large.states.remove(new BlockPos(1, 80, 8)); large.tiles.remove(new BlockPos(1, 80, 8)); large.markBlockRangeForRenderUpdate(1, 80, 8, 1, 80, 8);
            need(GlassSurfaceGroups.layout(first) != full && GlassSurfaceGroups.layout(first).columns == 1, "scan-limit member edit retained fallback cache");
            GlassSurfaceGroups.unload(large); need(GlassSurfaceGroups.layout(first) != full, "world unload retained layout");
        } finally { close(large); }
    }
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register(); com.vandorlabs.VandorLabs.proxy = new com.vandorlabs.CommonProxy();
        glass = new BlockProgrammableGlass("glass_layout_locality_checks");
        distant(); boundaries(); diagonalBoundaries(); carriedAndLimit();
        System.out.println("glass-layout-locality PASS distant block/range/chunk edits, bridges/holes/diagonal boundaries, materials, loaded boundary, scan cap, carried revision/snapshot and unload");
    }
    private GlassLayoutLocalityChecks() { }
}
