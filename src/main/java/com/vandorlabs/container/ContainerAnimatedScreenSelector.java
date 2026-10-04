package com.vandorlabs.container;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

/**
 * Server-side container for the screen selector GUI. The GUI is purely
 * configurational (no slots); this container only gates interaction range.
 */
public class ContainerAnimatedScreenSelector extends Container {

    private final TileEntityAnimatedScreenSelector tileEntity;
    public final int redstoneSlot;

    public ContainerAnimatedScreenSelector(InventoryPlayer playerInventory,
            TileEntityAnimatedScreenSelector tileEntity) {
        this(playerInventory,tileEntity,-1);
    }
    public ContainerAnimatedScreenSelector(InventoryPlayer playerInventory,TileEntityAnimatedScreenSelector tileEntity,int redstoneSlot){
        this.tileEntity = tileEntity;this.redstoneSlot=redstoneSlot;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return tileEntity != null && tileEntity.isUsableByPlayer(playerIn);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        return ItemStack.EMPTY;
    }

    public TileEntityAnimatedScreenSelector getTileEntity() {
        return tileEntity;
    }
}
