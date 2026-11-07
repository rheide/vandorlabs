package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;

/** Configurizer and ordinary creative sneak-click use one dedicated authorized configuration path. */
public final class NativeGlassConfiguration {
 public static boolean open(Player player,Level level,BlockPos pos,MenuType<NativeGlassMenu> menuType){
  if(menuType==null||!NativeConfigurationAccess.canEdit(player,level,pos)||!(level.getBlockEntity(pos) instanceof NativeGlassEntity owner)||!owner.available()||!(level.getBlockState(pos).getBlock() instanceof NativeGlassBlock))return false;
  if(level.isClientSide)return true;if(!(player instanceof ServerPlayer server)||!server.getServer().isSameThread())return false;
  server.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativeGlassMenu(menuType,window,owner),Component.literal(com.vandorlabs.ui.GlassDialog.TITLE)),buffer->buffer.writeBlockPos(pos));return true;
 }
 private NativeGlassConfiguration(){}
}
