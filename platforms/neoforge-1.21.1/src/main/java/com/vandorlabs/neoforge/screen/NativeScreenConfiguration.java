package com.vandorlabs.neoforge.screen;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
/** One inactive configuration route for the eventual ordinary interaction and Configurizer bindings. */
public final class NativeScreenConfiguration {
 private NativeScreenConfiguration(){}
 public static boolean open(Player player,Level level,BlockPos pos,MenuType<NativeScreenMenu> type){if(type==null||level instanceof ServerLevel server&&!NativeRedstoneChannels.open(server)||player instanceof ServerPlayer serverPlayer&&!serverPlayer.getServer().isSameThread()||!NativeConfigurationAccess.canEdit(player,level,pos)||!(level.getBlockEntity(pos) instanceof NativeScreenEntity tile)||tile.inert()||!(tile.getBlockState().getBlock() instanceof NativeScreenBlock block)||block.family()!=tile.family())return false;if(player instanceof ServerPlayer serverPlayer)serverPlayer.openMenu(new SimpleMenuProvider((id,inventory,ignored)->new NativeScreenMenu(type,id,tile),block.getName()),buffer->buffer.writeBlockPos(pos));return true;}
}
