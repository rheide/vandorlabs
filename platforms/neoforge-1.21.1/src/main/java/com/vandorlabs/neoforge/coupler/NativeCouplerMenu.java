package com.vandorlabs.neoforge.coupler;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public final class NativeCouplerMenu extends AbstractContainerMenu {
    public final NativeCouplerEntity owner;

    NativeCouplerMenu(MenuType<?> type, int window, NativeCouplerEntity owner) {
        super(type, window);
        this.owner = owner;
    }

    NativeCouplerMenu(MenuType<?> type, int window, Player player, BlockPos pos) {
        this(type, window, require(player, pos));
    }

    private static NativeCouplerEntity require(Player player, BlockPos pos) {
        if (!player.level().hasChunkAt(pos) || !(player.level().getBlockEntity(pos) instanceof NativeCouplerEntity owner))
            throw new IllegalArgumentException("Missing Coupler");
        return owner;
    }
    @Override public boolean stillValid(Player player) {
        return !owner.inert() && NativeConfigurationAccess.canEditDialog(player, owner)
                && !NativeTransferHolds.reserved(player.level(), owner.getBlockPos());
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        return id == 0 && player instanceof ServerPlayer && player.containerMenu == this && stillValid(player) && owner.toggle();
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
