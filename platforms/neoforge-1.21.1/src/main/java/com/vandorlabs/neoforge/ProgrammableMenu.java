package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Server-owned editing session bound to the exact installed tile identity. */
public final class ProgrammableMenu extends AbstractContainerMenu {
    public final ProgrammableBlockEntity tile;
    public ProgrammableMenu(int id,Inventory inventory,BlockPos pos) {
        super(VandorLabsModern.PROGRAMMABLE_MENU.get(),id);
        if(!(inventory.player.level().getBlockEntity(pos) instanceof ProgrammableBlockEntity entity))throw new IllegalArgumentException("Missing programmable block");
        tile=entity;
    }
    public static boolean canConfigure(Player player) { return NativeConfigurationAccess.canConfigure(player); }
    @Override public boolean stillValid(Player player) { return NativeConfigurationAccess.canEdit(player,tile); }
    @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
}
