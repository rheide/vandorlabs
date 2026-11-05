package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.redstone.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Shared binary activation and loaded support publication; family geometry stays in each block adapter. */
final class NativeMountedControlActions {
    static InteractionResult activate(NativeMountedControl control,BlockState state,Level level,BlockPos pos,Player player,float volume,float onPitch){
        if(player.isShiftKeyDown())return BinaryControlConfiguration.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!player.isAlive()||player.isSpectator()||!level.hasChunkAt(pos)||!level.mayInteract(player,pos)||level.getBlockState(pos).getBlock()!=control||!(level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile))return InteractionResult.PASS;
        boolean on=control.isMomentary()||!control.powered(state);
        NativeSignalUpdateBatch.apply(()->{
            if(!control.isMomentary()||!control.powered(state)){level.setBlock(pos,control.withPowered(state,on),2);tile.setLocalOn(on);tile.notifyPhysical();}
            if(control.isMomentary())level.scheduleTick(pos,state.getBlock(),20);
            level.playSound(null,pos,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,volume,on?onPitch:.5f);
        });return InteractionResult.SUCCESS;
    }
    static void neighborChanged(NativeMountedControl control,BlockState state,Level level,BlockPos pos){
        if(level instanceof ServerLevel server){BlockPos support=pos.relative(control.supportDirection(state));if(server.hasChunkAt(support)&&!state.canSurvive(server,pos))control.dropUnsupported(state,server,pos);}
    }
    static void removed(NativeMountedControl control,BlockState state,Level level,BlockPos pos,BlockState after){
        if(state.getBlock()==after.getBlock()||level.isClientSide)return;
        if(level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile)NativeRedstoneChannels.unregister(tile);
        if(control.powered(state)){BlockPos support=pos.relative(control.supportDirection(state));if(level.hasChunkAt(support))for(Direction side:Direction.values()){BlockPos target=support.relative(side);if(level.hasChunkAt(target))level.neighborChanged(target,state.getBlock(),pos);}}
    }
    private NativeMountedControlActions(){}
}
