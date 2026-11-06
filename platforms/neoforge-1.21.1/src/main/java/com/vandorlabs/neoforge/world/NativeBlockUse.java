package com.vandorlabs.neoforge.world;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

/** Call after family-specific target/reach/permission validation; retain vanilla and optional-mod item hooks. */
public final class NativeBlockUse {
    private NativeBlockUse(){}
    public static InteractionResult apply(ServerPlayer player,InteractionHand hand,BlockHitResult hit){
        if(!player.getServer().isSameThread()||!player.isAlive()||player.isSpectator()||hand==null||hit==null
                ||!NativeFluidWorld.loaded(player.serverLevel(),hit.getBlockPos()))return InteractionResult.FAIL;
        var result=player.gameMode.useItemOn(player,player.serverLevel(),player.getItemInHand(hand),hand,hit);
        player.inventoryMenu.broadcastChanges();return result;
    }
}
