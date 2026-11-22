package com.vandorlabs.neoforge.coupler;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NativeCouplerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    NativeCouplerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(facingProperty(), Direction.NORTH).setValue(LOCKED, false));
    }

    DirectionProperty facingProperty() { return FACING; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(NativeCouplerBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(facingProperty(), LOCKED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(facingProperty(), context.getClickedFace());
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(facingProperty(), rotation.rotate(facing(state)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(facingProperty(), mirror.mirror(facing(state)));
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NativeCouplerEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return NativeCouplerGeometry.shape(geometryKind(state), facing(state), state.getValue(LOCKED));
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof NativeCouplerEntity tile) tile.sample();
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player actor, BlockHitResult hit) {
        return NativeCouplerBindings.open(actor, level, pos) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player actor, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem || hand != InteractionHand.MAIN_HAND)
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return NativeCouplerBindings.open(actor, level, pos) ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    int geometryKind(BlockState state) { return 0; }

    public static Direction facing(BlockState state) {
        var block = (NativeCouplerBlock) state.getBlock();
        return state.getValue(block.facingProperty());
    }

    public static boolean pair(BlockState first, BlockState second, Direction fromFirst) {
        if (!(first.getBlock() instanceof NativeCouplerBlock a) || !(second.getBlock() instanceof NativeCouplerBlock b))
            return false;
        return first.getValue(LOCKED) && second.getValue(LOCKED) && facing(first) == fromFirst
                && facing(second) == fromFirst.getOpposite() && a.geometryKind(first) == b.geometryKind(second);
    }

    static final class Slab extends NativeCouplerBlock {
        static final DirectionProperty HEAD = BlockStateProperties.HORIZONTAL_FACING;

        Slab(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HALF, Half.BOTTOM));
        }
        @Override DirectionProperty facingProperty() { return HEAD; }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(Slab::new); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(BlockStateProperties.HALF);
        }
        @Override int geometryKind(BlockState state) { return state.getValue(BlockStateProperties.HALF) == Half.TOP ? 2 : 1; }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            var face = context.getClickedFace();
            boolean top = face == Direction.DOWN || face.getAxis().isHorizontal()
                    && context.getClickLocation().y - context.getClickedPos().getY() > .5;
            return defaultBlockState()
                    .setValue(HEAD, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite())
                    .setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
        }
    }
}
