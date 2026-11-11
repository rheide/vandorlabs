package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.render.GlassConnections;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Compare complete model states with the pre-optimization algorithm and count world reads. */
public final class GlassActualStateChecks {
    private static final BlockPos POS = new BlockPos(8, 80, 8);
    private static final class World extends NonRenderingChecks.MemoryWorld {
        int tileReads;
        World() { super(true); }
        @Override public TileEntity getTileEntity(BlockPos pos) { tileReads++; return super.getTileEntity(pos); }
        void resetReads() { stateReads = tileReads = 0; }
    }
    private static void need(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    private static boolean joins(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return !(tile instanceof TileEntityProgrammableGlass) || ((TileEntityProgrammableGlass) tile).isJoin();
    }
    // Frozen BlockGlassWall/BlockProgrammableGlass state algorithm from e8cc78a1.
    // Keep this in its original discovery-before-suppression order for cost evidence.
    private static boolean connects(World world, BlockPos pos, IBlockState state) {
        EnumFacing normal = PanelJoins.facing(state);
        for (int offset = -1; offset <= 1; offset++) {
            BlockPos candidate = pos.offset(normal, offset);
            if (PanelJoins.diagonal(world.getBlockState(candidate))
                    && PanelJoins.connectsFlat(world, POS, state, candidate)) return true;
        }
        IBlockState other = world.getBlockState(pos);
        if (PanelJoins.flat(other) && !(other.getBlock() instanceof BlockGlassWall))
            return PanelJoins.connectsFlat(world, POS, state, pos);
        other = world.getBlockState(pos);
        return state.getBlock() == other.getBlock() && joins(world, pos)
                && other.getValue(BlockGlassWall.ROTATED).equals(state.getValue(BlockGlassWall.ROTATED))
                && other.getValue(BlockGlassWall.DEPTH).equals(state.getValue(BlockGlassWall.DEPTH));
    }
    private static IBlockState before(World world, IBlockState state) {
        GlassConnections edges;
        if (!joins(world, POS)) edges = GlassConnections.calculate((x, y) -> false);
        else {
            EnumFacing left = state.getValue(BlockGlassWall.ROTATED) ? EnumFacing.NORTH : EnumFacing.WEST;
            edges = GlassConnections.calculate((x, y) -> connects(world, POS.offset(left, -x).up(y), state));
        }
        IBlockState actual = state.withProperty(BlockGlassWall.TOP, edges.top).withProperty(BlockGlassWall.BOTTOM, edges.bottom)
                .withProperty(BlockGlassWall.LEFT, edges.left).withProperty(BlockGlassWall.RIGHT, edges.right)
                .withProperty(BlockGlassWall.INNER_TL, edges.innerTopLeft).withProperty(BlockGlassWall.INNER_TR, edges.innerTopRight)
                .withProperty(BlockGlassWall.INNER_BL, edges.innerBottomLeft).withProperty(BlockGlassWall.INNER_BR, edges.innerBottomRight);
        TileEntity raw = world.getTileEntity(POS);
        TileEntityProgrammableGlass tile = raw instanceof TileEntityProgrammableGlass ? (TileEntityProgrammableGlass) raw : null;
        if (PanelJoins.flatCorner(state, world, POS) != null || tile != null && (!tile.isFramed()
                || tile.frameAppearance.front >= 0 || tile.frameAppearance.interior >= 0
                || tile.frameAppearance.exteriorChoice() >= 0 || tile.frameAppearance.tile))
            actual = actual.withProperty(BlockGlassWall.TOP, false).withProperty(BlockGlassWall.BOTTOM, false)
                    .withProperty(BlockGlassWall.LEFT, false).withProperty(BlockGlassWall.RIGHT, false)
                    .withProperty(BlockGlassWall.INNER_TL, false).withProperty(BlockGlassWall.INNER_TR, false)
                    .withProperty(BlockGlassWall.INNER_BL, false).withProperty(BlockGlassWall.INNER_BR, false);
        return actual.withProperty(BlockProgrammableGlass.SIZE, tile == null ? Math.min(state.getValue(BlockProgrammableGlass.SIZE), 1) : tile.getSize());
    }
    private static TileEntityProgrammableGlass put(World world, BlockPos pos, IBlockState state, boolean join, int appearance, int size) {
        TileEntityProgrammableGlass tile = new TileEntityProgrammableGlass();
        tile.setJoin(join); tile.setSize(size); tile.setAppearance(1, appearance != 1);
        if (appearance == 2) tile.frameAppearance.front = 0;
        if (appearance == 3) tile.frameAppearance.interior = 0;
        if (appearance == 4) tile.frameAppearance.exterior = 0;
        if (appearance == 5) tile.frameAppearance.tile = true;
        if (appearance == 6) { tile.frameAppearance.interior = 0; tile.frameAppearance.exterior = -1; }
        tile.setWorld(world); tile.setPos(pos); world.states.put(pos, state); world.tiles.put(pos, tile); return tile;
    }
    private static void check(BlockProgrammableGlass glass, World world, IBlockState state, boolean separate) {
        IBlockState expected = before(world, state);
        world.resetReads(); IBlockState actual = glass.getActualState(state, world, POS);
        need(actual.equals(expected), "listed model state changed: " + expected + " / " + actual);
        if (separate) need(world.stateReads == 0 && world.tileReads == 1, "separate frame queried neighbours");
    }
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        com.vandorlabs.VandorLabs.proxy = new com.vandorlabs.CommonProxy();
        BlockProgrammableGlass glass = new BlockProgrammableGlass("glass_actual_state_checks");
        World world = new World(); int cases = 0;
        for (boolean rotated : new boolean[]{false, true}) for (int depth = 0; depth < 3; depth++)
            for (boolean join : new boolean[]{false, true}) for (int appearance = 0; appearance < 7; appearance++)
                for (int mask = 0; mask < 256; mask++) {
                    world.clear();
                    IBlockState state = glass.getDefaultState().withProperty(BlockGlassWall.ROTATED, rotated)
                            .withProperty(BlockGlassWall.DEPTH, depth).withProperty(BlockProgrammableGlass.SIZE, mask % 3);
                    put(world, POS, state, join, appearance, mask & 1);
                    EnumFacing right = rotated ? EnumFacing.SOUTH : EnumFacing.EAST; int bit = 0;
                    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) if (x != 0 || y != 0) {
                        if ((mask & (1 << bit)) != 0) put(world, POS.offset(right, x).up(y), state, true, 0, 1);
                        bit++;
                    }
                    check(glass, world, state, appearance != 0); cases++;
                }
        for (EnumFacing facing : EnumFacing.HORIZONTALS) for (int depth = 0; depth < 3; depth++)
            for (int appearance = 0; appearance < 7; appearance++) for (int shape = 0; shape < 4; shape++) {
                world.clear(); IBlockState state = glass.getDefaultState().withProperty(BlockGlassWall.ROTATED, facing.getAxis() == EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH, depth);
                TileEntityProgrammableGlass root = put(world, POS, state, true, appearance, 1); root.setPanelFacing(facing);
                IBlockState cross = state.withProperty(BlockGlassWall.ROTATED, !state.getValue(BlockGlassWall.ROTATED));
                put(world, POS.offset(facing), cross, true, 0, 1);
                if (shape >= 1) put(world, POS.offset(facing.getOpposite()), cross, true, 0, 1);
                if (shape >= 2) put(world, POS.offset(facing.rotateY()), state, true, 0, 1);
                if (shape >= 3) put(world, POS.offset(facing.rotateYCCW()), state, true, 0, 1);
                check(glass, world, state, appearance != 0); cases++;
            }
        world.clear(); IBlockState state = glass.getDefaultState().withProperty(BlockProgrammableGlass.SIZE, 2);
        world.states.put(POS, state); check(glass, world, state, false); cases++;
        put(world, POS, state, true, 1, 1); world.resetReads(); before(world, state);
        int oldStates = world.stateReads, oldTiles = world.tileReads;
        world.resetReads(); glass.getActualState(state, world, POS);
        System.out.println("glass-actual-state PASS cases=" + cases + " frameless-isolated old/new stateReads=" + oldStates + "/" + world.stateReads + " tileReads=" + oldTiles + "/" + world.tileReads);
    }
    private GlassActualStateChecks() { }
}
