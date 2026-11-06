package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

/** Ordinary sneaking interaction and Configurizer access use this same path. */
public final class NativeSegmentedConfiguration {
    private NativeSegmentedConfiguration(){}
    public static boolean open(Player player,Level level,BlockPos pos,MenuType<NativeSegmentedMenu> type){
        if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof NativeSegmentedDoorEntity tile)
                ||tile.inert()||!NativeConfigurationAccess.canEditDialog(player,tile))return false;
        if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider(
                (id,inventory,title)->new NativeSegmentedMenu(type,id,tile),Component.literal("Segmented Door")),
                buffer->buffer.writeBlockPos(pos));
        return true;
    }
}
