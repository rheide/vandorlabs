package com.vandorlabs.neoforge;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

/** Native full cube; settings are supplied by the shared current housing contract. */
public class ProgrammableBlock extends DirectionalBlock implements net.minecraft.world.level.block.EntityBlock {
    public static final MapCodec<ProgrammableBlock> CODEC = simpleCodec(ProgrammableBlock::new);
    public ProgrammableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ProgrammableBlockEntity(pos,state); }
    @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState adjacent=context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        return defaultBlockState().setValue(FACING,adjacent.is(this)?adjacent.getValue(FACING):context.getHorizontalDirection().getOpposite());
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult target,net.minecraft.world.level.LevelReader level,BlockPos pos,Player player) {
        var stack=new net.minecraft.world.item.ItemStack(this);
        if(level.getBlockEntity(pos) instanceof ProgrammableBlockEntity tile)tile.saveToItem(stack,level.registryAccess());
        return stack;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return player.isShiftKeyDown()&&CubeConfiguration.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;
    }
}
