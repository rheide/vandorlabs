package com.vandorlabs.container;

import com.vandorlabs.items.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;

/** Keeps Duplifier option packets tied to the held item and open dialog. */
public final class ContainerDuplifier extends Container {
    private final int selectedSlot;

    public ContainerDuplifier(InventoryPlayer inventory) {
        selectedSlot = inventory.currentItem;
    }

    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.inventory.currentItem == selectedSlot
                && player.getHeldItemMainhand().getItem() == ModItems.DUPLIFIER;
    }
}
