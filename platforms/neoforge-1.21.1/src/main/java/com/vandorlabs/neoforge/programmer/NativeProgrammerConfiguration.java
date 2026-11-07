package com.vandorlabs.neoforge.programmer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
/** Common dedicated configuration entry; callers retain authored socket/journal precedence. */
public final class NativeProgrammerConfiguration {
 private NativeProgrammerConfiguration(){}
 public static boolean open(ServerPlayer player,ServerLevel level,BlockPos root,MenuType<NativeProgrammerMenu> type){if(type==null||!player.getServer().isSameThread()||!level.hasChunkAt(root)||!(level.getBlockEntity(root) instanceof NativeProgrammerEntity tile)||!tile.usable(player))return false;player.openMenu(new SimpleMenuProvider((id,inventory,actor)->new NativeProgrammerMenu(type,id,inventory,tile),Component.translatable("block.vandorlabs.data_chip_programmer")),root);return true;}
}
