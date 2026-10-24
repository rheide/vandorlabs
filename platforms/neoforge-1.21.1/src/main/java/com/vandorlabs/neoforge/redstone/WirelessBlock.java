package com.vandorlabs.neoforge.redstone;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** The authored port is the only wired input/output; all six faces are supported. */
public final class WirelessBlock extends DirectionalBlock implements EntityBlock {
    public final boolean transmitter;
    public WirelessBlock(boolean transmitter, BlockBehaviour.Properties properties) {
        super(properties); this.transmitter = transmitter;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends DirectionalBlock> codec() { return simpleCodec(properties -> new WirelessBlock(transmitter, properties)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WirelessBlockEntity(pos, state); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        Direction face = Direction.NORTH;
        BlockPos pos = context.getClickedPos();
        if (player != null) {
            face = player.getDirection().getOpposite();
            if (Math.abs(player.getX() - pos.getX()) < 2 && Math.abs(player.getZ() - pos.getZ()) < 2) {
                if (player.getEyeY() - pos.getY() > 2) face = Direction.UP;
                else if (pos.getY() > player.getEyeY()) face = Direction.DOWN;
            }
        }
        return defaultBlockState().setValue(FACING, face);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(FACING, mirror.mirror(state.getValue(FACING))); }
    @Override protected boolean isSignalSource(BlockState state) { return !transmitter; }
    @Override public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) { return side == state.getValue(FACING).getOpposite(); }
    @Override public boolean shouldCheckWeakPower(BlockState state, SignalGetter world, BlockPos pos, Direction side) { return false; }
    @Override protected int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        return !transmitter && side == state.getValue(FACING).getOpposite() && world.getBlockEntity(pos) instanceof WirelessBlockEntity tile ? tile.outputLevel() : 0;
    }
    @Override protected int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction side) { return getSignal(state, world, pos, side); }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WirelessBlockEntity tile) tile.sampleInput();
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock()) {
            if (level.getBlockEntity(pos) instanceof WirelessBlockEntity tile) ModernRedstoneChannels.unregister(tile);
            super.onRemove(state, level, pos, next, moving);
            level.updateNeighborsAt(pos, this);
            if (level.hasChunkAt(pos.relative(state.getValue(FACING)))) level.updateNeighborsAt(pos.relative(state.getValue(FACING)), this);
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player, level, pos)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof WirelessBlockEntity tile) {
            WirelessMenu menu = new WirelessMenu(0, player.getInventory(), pos);
            if (!menu.stillValid(player)) return InteractionResult.FAIL;
            tile.sync();
            server.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new WirelessMenu(id, inventory, pos),
                    net.minecraft.network.chat.Component.literal(transmitter ? "Redstone Transmitter" : "Redstone Receiver")), buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
