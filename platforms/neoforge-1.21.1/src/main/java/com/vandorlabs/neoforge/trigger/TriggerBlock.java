package com.vandorlabs.neoforge.trigger;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.ProgrammableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Solid, unlit programmable cube; only its signal consumer and settings menu differ. */
public final class TriggerBlock extends ProgrammableBlock {
    private static final MapCodec<TriggerBlock> TRIGGER_CODEC=simpleCodec(TriggerBlock::new);
    public TriggerBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return TRIGGER_CODEC;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TriggerEntity(pos,state);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof TriggerEntity trigger)trigger.refreshSignal();}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.isShiftKeyDown()&&!player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsModern.CONFIGURIZER.get()))return InteractionResult.PASS;
        if(level.getBlockEntity(pos) instanceof TriggerEntity trigger&&com.vandorlabs.neoforge.NativeConfigurationAccess.canEdit(player,trigger)){if(player instanceof ServerPlayer server)TriggerMenu.open(server,trigger);return InteractionResult.sidedSuccess(level.isClientSide);}return InteractionResult.PASS;
    }
}
