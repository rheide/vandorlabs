package com.vandorlabs.neoforge.seating;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The anchor owns configuration and rendering. The other three cells own only a seat reservation. */
class NativeAdvancedChairBlock extends NativeSeatBlock {
    static final IntegerProperty SIDE = IntegerProperty.create("side", 0, 2);
    private static final ThreadLocal<Change> ACTIVE = new ThreadLocal<>();
    private record Change(Level level, BlockPos root, Direction facing, NativeAdvancedChairBlock block) {}
    private record Step(BlockPos pos, BlockState before, BlockState after) {}

    NativeAdvancedChairBlock(BlockBehaviour.Properties properties) {
        this(NativeSeatKind.ADVANCED, properties);
    }

    NativeAdvancedChairBlock(NativeSeatKind kind, BlockBehaviour.Properties properties) {
        super(kind, properties);
        registerDefaultState(defaultBlockState().setValue(lateralProperty(), 0));
    }

    @Override
    protected MapCodec<? extends NativeSeatBlock> codec() { return simpleCodec(NativeAdvancedChairBlock::new); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(lateralProperty());
    }

    protected IntegerProperty lateralProperty() { return SIDE; }

    int partIndex(BlockState state) {
        if (state.getValue(UPPER)) return NativeAdvancedChairGeometry.UPPER;
        return switch (state.getValue(lateralProperty())) {
            case 1 -> NativeAdvancedChairGeometry.LEFT;
            case 2 -> NativeAdvancedChairGeometry.RIGHT;
            default -> NativeAdvancedChairGeometry.ROOT;
        };
    }

    static BlockPos root(BlockState state, BlockPos pos) {
        var block = (NativeAdvancedChairBlock) state.getBlock();
        return pos.subtract(block.offset(block.partIndex(state), state.getValue(FACING)));
    }

    BlockState stateFor(BlockState root, int part) {
        return root.setValue(UPPER, part == NativeAdvancedChairGeometry.UPPER)
            .setValue(lateralProperty(), part >= NativeAdvancedChairGeometry.LEFT ? part - 1 : 0);
    }

    int cells() { return NativeAdvancedChairGeometry.CELLS; }

    BlockPos offset(int part, Direction facing) {
        return NativeAdvancedChairGeometry.offset(part, facing);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        var owner = owner(world, pos);
        if (owner == null && proxy(state)) return Shapes.empty();
        return NativeAdvancedChairGeometry.shape(state.getValue(FACING), owner == null ? 0 : owner.pilotHeight(), partIndex(state));
    }

    Direction placementFacing(BlockPlaceContext context) {
        return context.getHorizontalDirection().getOpposite();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var root = defaultBlockState().setValue(FACING, placementFacing(context));
        return plan(context, root) == null ? null : root;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity actor, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof NativeChairEntity owner) owner.placed();
    }

    @Override
    public boolean complete(BlockGetter world, BlockPos root) {
        if (!loaded(world, root)) return false;
        var anchor = world.getBlockState(root);
        if (!anchor.is(this) || proxy(anchor)) return false;
        var face = anchor.getValue(FACING);
        for (int part = 1; part < cells(); part++) {
            var pos = root.offset(offset(part, face));
            if (!loaded(world, pos) || world.getBlockState(pos) != stateFor(anchor, part)
                || !(world.getBlockEntity(pos) instanceof NativeChairEntity proxy) || !proxy.proxyShareable()) return false;
        }
        return true;
    }

    boolean install(BlockPlaceContext context, BlockState rootState) {
        var plan = plan(context, rootState);
        if (plan == null) return false;
        var level = context.getLevel();
        var previous = ACTIVE.get();
        ACTIVE.set(new Change(level, context.getClickedPos(), rootState.getValue(FACING), this));
        boolean success = false;
        int staged = 0;
        try {
            for (var step : plan) {
                staged++;
                if (!level.setBlock(step.pos(), step.after(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                    || level.getBlockState(step.pos()) != step.after()
                    || !(level.getBlockEntity(step.pos()) instanceof NativeChairEntity)) return false;
            }
            success = complete(level, context.getClickedPos());
            return success;
        } finally {
            try {
                if (!success) {
                    IllegalStateException rollback = null;
                    for (int i = staged - 1; i >= 0; i--) {
                        var step = plan.get(i);
                        if (level.getBlockState(step.pos()) == step.after()
                            && !level.setBlock(step.pos(), step.before(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                            && level.getBlockState(step.pos()) != step.before()) {
                            var failure = new IllegalStateException("Advanced Pilot Chair placement rollback refused at " + step.pos());
                            if (rollback == null) rollback = failure;
                            else rollback.addSuppressed(failure);
                        }
                    }
                    if (rollback != null) throw rollback;
                }
            } finally {
                if (previous == null) ACTIVE.remove();
                else ACTIVE.set(previous);
            }
        }
    }

    private List<Step> plan(BlockPlaceContext context, BlockState rootState) {
        var player = context.getPlayer();
        var properties = context.getItemInHand().get(DataComponents.BLOCK_STATE);
        if (player == null || properties != null && properties.apply(rootState) != rootState) return null;
        var root = context.getClickedPos();
        var level = context.getLevel();
        var facing = rootState.getValue(FACING);
        var result = new ArrayList<Step>(cells());
        for (int part = 0; part < cells(); part++) {
            var pos = root.offset(offset(part, facing));
            if (!level.isInWorldBounds(pos) || !loaded(level, pos) || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())
                || com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level, pos)) return null;
            var state = level.getBlockState(pos);
            if (!state.canBeReplaced(context) || state.hasBlockEntity() || level.getBlockEntity(pos) != null) return null;
            result.add(new Step(pos, state, stateFor(rootState, part)));
        }
        return result;
    }

    void removeAssembly(Level level, BlockPos pos, BlockState old, boolean moving) {
        if (level.isClientSide || mutating(level, pos)) return;
        var root = root(old, pos);
        var anchor = stateFor(old, NativeAdvancedChairGeometry.ROOT);
        if (!pos.equals(root) && (!loaded(level, root) || level.getBlockState(root) != anchor)) return;
        var facing = old.getValue(FACING);
        var previous = ACTIVE.get();
        ACTIVE.set(new Change(level, root, facing, this));
        try {
            for (var mount : level.getEntitiesOfClass(NativeChairMount.class, new AABB(root).inflate(2))) {
                if (root.equals(mount.chairPos())) {
                    mount.ejectPassengers();
                    mount.discard();
                }
            }
            for (int part = 0; part < cells(); part++) {
                var cell = root.offset(offset(part, facing));
                if (cell.equals(pos) || !loaded(level, cell) || level.getBlockState(cell) != stateFor(anchor, part)) continue;
                if (part == NativeAdvancedChairGeometry.ROOT
                    || level.getBlockEntity(cell) instanceof NativeChairEntity extension && extension.proxyShareable()) {
                    level.removeBlock(cell, moving);
                }
            }
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    void repair(ServerLevel level, NativeChairEntity tile) {
        var state = tile.getBlockState();
        var pos = tile.getBlockPos();
        if (tile.inert()) return;
        if (proxy(state)) {
            var root = root(state, pos);
            if (loaded(level, root) && rawOwner(level, pos) == null && tile.proxyShareable()) level.removeBlock(pos, false);
            return;
        }
        var facing = state.getValue(FACING);
        for (int part = 1; part < cells(); part++) {
            var cell = pos.offset(offset(part, facing));
            if (loaded(level, cell) && level.isInWorldBounds(cell) && level.isEmptyBlock(cell)
                && level.getBlockEntity(cell) == null
                && !com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level, cell)) {
                level.setBlock(cell, stateFor(state, part), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    static boolean mutating(Level level, BlockPos pos) {
        var change = ACTIVE.get();
        if (change == null || change.level() != level) return false;
        for (int part = 0; part < change.block().cells(); part++) {
            if (pos.equals(change.root().offset(change.block().offset(part, change.facing())))) return true;
        }
        return false;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        var reflected = super.mirror(state, mirror);
        int side = reflected.getValue(SIDE);
        return mirror == Mirror.NONE || side == 0 ? reflected : reflected.setValue(SIDE, 3 - side);
    }
}
