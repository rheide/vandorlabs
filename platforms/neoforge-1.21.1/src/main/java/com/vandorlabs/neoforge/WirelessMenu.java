package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** One exact installed endpoint session; no transmitter inventory or item consumption. */
public final class WirelessMenu extends AbstractContainerMenu {
    public final NativeWirelessRedstoneBlockEntity tile;
    public WirelessMenu(int id,Inventory inventory,BlockPos pos){super(VandorLabsBootstrap.WIRELESS_MENU.get(),id);if(inventory.player.level() instanceof net.minecraft.server.level.ServerLevel server&&!com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))throw new IllegalArgumentException("Wireless world is unloaded");if(!inventory.player.level().hasChunkAt(pos)||!(inventory.player.level().getBlockEntity(pos) instanceof NativeWirelessRedstoneBlockEntity endpoint))throw new IllegalArgumentException("Missing wireless endpoint");tile=endpoint;}
    @Override public boolean stillValid(Player player){return (!(tile.getLevel() instanceof net.minecraft.server.level.ServerLevel server)||com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))&&tile.getBlockState().getBlock() instanceof NativeWirelessRedstoneBlock&&NativeConfigurationAccess.canEditDialog(player,tile);}
    static NativeWirelessRedstoneBlockEntity editing(ServerPlayer player,BlockPos pos,int window){if(!player.getServer().isSameThread()||!(player.containerMenu instanceof WirelessMenu menu)||menu.containerId!=window||!menu.tile.getBlockPos().equals(pos)||!menu.stillValid(player))return null;return menu.tile;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
