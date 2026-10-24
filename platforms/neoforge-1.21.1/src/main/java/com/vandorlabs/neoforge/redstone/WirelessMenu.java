package com.vandorlabs.neoforge.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Settings sessions stay bound to the exact installed endpoint and editable loaded position. */
public final class WirelessMenu extends AbstractContainerMenu {
    public final WirelessBlockEntity tile;
    public WirelessMenu(int id, Inventory inventory, BlockPos pos) {
        super(WirelessContent.MENU.get(), id);
        if (!(inventory.player.level().getBlockEntity(pos) instanceof WirelessBlockEntity entity)) throw new IllegalArgumentException("Missing wireless endpoint");
        tile = entity;
    }
    @Override public boolean stillValid(Player player) {
        return com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player, tile);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
