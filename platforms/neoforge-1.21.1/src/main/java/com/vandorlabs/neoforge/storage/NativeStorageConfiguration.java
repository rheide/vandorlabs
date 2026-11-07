package com.vandorlabs.neoforge.storage;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
/** Configurizer and Creative sneak use the same dedicated material session. No global registration. */
public final class NativeStorageConfiguration {
 private NativeStorageConfiguration(){}
 public static boolean open(Player player,Level level,BlockPos pos,MenuType<NativeStorageMenu> type){if(type==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof NativeStorageEntity owner)||!owner.available()||!NativeConfigurationAccess.canEdit(player,owner)||!level.isClientSide&&!owner.installed())return false;if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider((id,inventory,ignored)->new NativeStorageMenu(type,id,owner),owner.getDisplayName()),buffer->buffer.writeBlockPos(pos));return true;}
}
