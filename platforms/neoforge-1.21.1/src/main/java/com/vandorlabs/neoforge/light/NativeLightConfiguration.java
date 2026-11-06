package com.vandorlabs.neoforge.light;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
/** Shared dedicated entry point for Configurizer and ordinary creative-sneak configuration. */
public final class NativeLightConfiguration {
 private NativeLightConfiguration(){}
 public static boolean open(Player player,Level world,BlockPos pos,MenuType<NativeLightMenu> type){if(type==null||!world.hasChunkAt(pos)||!(world.getBlockEntity(pos) instanceof NativeLightEntity owner)||!owner.available()||!NativeConfigurationAccess.canEdit(player,owner))return false;if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider((id,inventory,title)->new NativeLightMenu(type,id,owner),Component.literal("Programmable Light")),buffer->buffer.writeBlockPos(pos));return true;}
}
