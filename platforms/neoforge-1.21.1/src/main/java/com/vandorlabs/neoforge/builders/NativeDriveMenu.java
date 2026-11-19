package com.vandorlabs.neoforge.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class NativeDriveMenu extends AbstractContainerMenu {
    public final NativeBuilderEntity tile;
    final BlockPos target;
    private final Block block;

    NativeDriveMenu(MenuType<?> type, int window, NativeBuilderEntity tile, BlockPos target) {
        super(type, window);
        this.tile = tile;
        this.target = target.immutable();
        block = tile.getBlockState().getBlock();
    }

    NativeDriveMenu(MenuType<?> type, int window, Inventory inventory, BlockPos root, BlockPos target) {
        this(type, window, require(inventory.player, root), target);
    }

    private static NativeBuilderEntity require(Player player, BlockPos root) {
        var owner = NativeBuilderBindings.ownerAt(player.level(), root);
        if (owner == null || !owner.getBlockPos().equals(root) || owner.drive() == null)
            throw new IllegalArgumentException("Missing loaded drive");
        return owner;
    }

    @Override public boolean stillValid(Player player) {
        return tile.getBlockState().getBlock() == block && NativeDriveBindings.canUse(player, tile, target);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
