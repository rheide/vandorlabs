package com.vandorlabs.container;
import com.vandorlabs.tiles.TileEntityChipAppliance;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
/** Standard machine ownership slots; vanilla container IDs authorize Print/Scan actions. */
public final class ContainerChipAppliance extends Container {
    public final TileEntityChipAppliance tile;private final int inputs;
    public ContainerChipAppliance(InventoryPlayer player,TileEntityChipAppliance tile){this.tile=tile;inputs=tile.getSizeInventory();for(int i=0;i<inputs;i++){final int slot=i;addSlotToContainer(new Slot(tile,i,40+i*72,74){@Override public boolean isItemValid(ItemStack stack){return tile.isItemValidForSlot(slot,stack);}@Override public int getSlotStackLimit(){return tile.slotLimit(slot);}});}for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,9+row*9+col,100+col*18,124+row*18));for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col,100+col*18,182));}
    @Override public boolean canInteractWith(EntityPlayer player){return tile.isUsableByPlayer(player)&&player.getDistanceSqToCenter(tile.getPos())<=64;}
    @Override public boolean enchantItem(EntityPlayer player,int action){if(action!=0||player.openContainer!=this||!canInteractWith(player))return false;tile.activate(player);return true;}
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){if(index<0||index>=inventorySlots.size()||!canInteractWith(player))return ItemStack.EMPTY;Slot slot=inventorySlots.get(index);if(!slot.getHasStack())return ItemStack.EMPTY;ItemStack stack=slot.getStack(),before=stack.copy();boolean moved=false;if(index<inputs)moved=mergeItemStack(stack,inputs,inventorySlots.size(),true);else for(int i=0;i<inputs;i++)if(tile.isItemValidForSlot(i,stack)){moved=mergeItemStack(stack,i,i+1,false);break;}if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();slot.onTake(player,stack);return before;}
}
