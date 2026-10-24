package com.vandorlabs.neoforge.screen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public final class ProgrammableScreenBlock extends DirectionalBlock implements EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty LIGHT=net.minecraft.world.level.block.state.properties.IntegerProperty.create("light",0,15);
    public static final MapCodec<ProgrammableScreenBlock> CODEC=simpleCodec(ProgrammableScreenBlock::new);
    public ProgrammableScreenBlock(BlockBehaviour.Properties properties){super(properties.lightLevel(state->state.getValue(LIGHT)));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LIGHT,11));}
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,LIGHT);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockState adjacent=context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        return defaultBlockState().setValue(FACING,adjacent.is(this)?adjacent.getValue(FACING):context.getNearestLookingDirection().getOpposite());
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ProgrammableScreenEntity(pos,state);}
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult target,net.minecraft.world.level.LevelReader level,BlockPos pos,Player player){
        var stack=new net.minecraft.world.item.ItemStack(ScreenRegistration.ITEM.get());if(level.getBlockEntity(pos) instanceof ProgrammableScreenEntity screen)screen.saveToItem(stack,level.registryAccess());return stack;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.isShiftKeyDown()&&!player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsModern.CONFIGURIZER.get())&&level.getBlockEntity(pos) instanceof ProgrammableScreenEntity screen&&screen.redstone().enabled())return RedstoneScreenInteraction.activate(screen,player)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;
        if(!player.isAlive()||player.isSpectator()||!player.mayBuild()||!com.vandorlabs.neoforge.ProgrammableMenu.canConfigure(player)||!(player.isCreative()&&player.isShiftKeyDown()||player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsModern.CONFIGURIZER.get())))return InteractionResult.PASS;
        if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider((id,inventory,p)->new ScreenMenu(id,inventory,pos),Component.translatable("gui.vandorlabs.selector.title")),buffer->{buffer.writeBlockPos(pos);buffer.writeBoolean(false);});
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof ProgrammableScreenEntity screen)screen.refreshSignal();}
}
