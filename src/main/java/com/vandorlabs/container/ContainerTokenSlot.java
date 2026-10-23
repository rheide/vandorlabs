package com.vandorlabs.container;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
/** Configuration only. The shared credential picker samples inventory without moving items. */
public final class ContainerTokenSlot extends Container {
 public final TileEntityTokenSlot tile;
 public ContainerTokenSlot(InventoryPlayer player,TileEntityTokenSlot tile){this.tile=tile;}
 @Override public boolean canInteractWith(EntityPlayer player){return tile.isUsableByPlayer(player);}
 @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){return ItemStack.EMPTY;}
}
