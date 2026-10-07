package com.vandorlabs.container;

import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.items.ItemProgrammableArmor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** Bind edits to the exact held stack and hotbar slot that opened the dialog. */
public final class ContainerProgrammableArmor extends Container {
    public final EnumHand hand;
    public final ItemStack armor;
    private final EntityPlayer owner;
    private final int hotbar;
    public ContainerProgrammableArmor(EntityPlayer player, EnumHand hand) {
        this.owner = player;
        this.hand = hand;
        this.armor = player.getHeldItem(hand);
        this.hotbar = player.inventory.currentItem;
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player == owner && player.isEntityAlive() && ConfigurationAccess.canConfigure(player)
                && armor.getItem() instanceof ItemProgrammableArmor && !armor.isEmpty()
                && player.getHeldItem(hand) == armor
                && (hand == EnumHand.OFF_HAND || player.inventory.currentItem == hotbar);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }
}
