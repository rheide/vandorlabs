package com.vandorlabs.neoforge;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class NativeTriggerBlock extends ProgrammableBlock {
    public static final MapCodec<NativeTriggerBlock> CODEC=simpleCodec(NativeTriggerBlock::new);
    public NativeTriggerBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeTriggerBlockEntity(pos,state);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeTriggerBlockEntity tile)tile.localInputChanged();
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide||type!=VandorLabsBootstrap.TRIGGER_ENTITY.get()?null:(world,pos,current,tile)->((NativeTriggerBlockEntity)tile).tickLocalInput();
    }
}
