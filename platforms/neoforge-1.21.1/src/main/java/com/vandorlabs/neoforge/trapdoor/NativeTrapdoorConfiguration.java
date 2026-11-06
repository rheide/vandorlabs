package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;

/** Shared entry point for ordinary configuration and Configurizer use, awaiting catalog activation. */
public final class NativeTrapdoorConfiguration {
    private NativeTrapdoorConfiguration() { }
    public static boolean open(Player player,Level level,BlockPos pos,MenuType<NativeTrapdoorMenu> type){
        var tile=NativeTrapdoorEntity.at(level,pos);
        if(tile==null||!NativeConfigurationAccess.canEdit(player,tile))return false;
        if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider(
                (id,inventory,title)->new NativeTrapdoorMenu(type,id,tile),
                Component.literal(tile.diagonal()?"Programmable Diagonal Trapdoor":"Programmable Trapdoor")),buffer->buffer.writeBlockPos(pos));
        return true;
    }
}
