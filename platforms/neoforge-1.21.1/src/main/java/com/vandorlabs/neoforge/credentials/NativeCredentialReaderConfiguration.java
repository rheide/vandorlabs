package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;

/** One Source-authorized path for ordinary sneaking and Configurizer interaction. */
public final class NativeCredentialReaderConfiguration {
    private NativeCredentialReaderConfiguration(){}
    public static boolean open(Player player,Level level,BlockPos pos,MenuType<NativeCredentialReaderMenu> type){
        if(type==null||level instanceof ServerLevel server&&(!server.getServer().isSameThread()||!NativeRedstoneChannels.open(server))||!NativeConfigurationAccess.canEditDialog(player,level,pos)||!(level.getBlockEntity(pos) instanceof NativeCredentialReaderEntity owner)||owner.isRemoved()||owner.inert()&&!level.isClientSide)return false;
        if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider((id,inventory,ignored)->new NativeCredentialReaderMenu(type,id,owner),owner.getBlockState().getBlock().getName()),buffer->buffer.writeBlockPos(pos));return true;
    }
}
