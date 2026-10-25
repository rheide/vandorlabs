package com.vandorlabs.programmer;

import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;

public final class ProgrammerContainer extends Container {
    public final ProgrammerTile tile;
    public ProgrammerContainer(InventoryPlayer inventory,ProgrammerTile tile){
        this.tile=tile;
        for(int i=0;i<3;i++){final int slot=i;addSlotToContainer(new Slot(tile,i,18+i*90,35){@Override public boolean isItemValid(ItemStack stack){return tile.isItemValidForSlot(slot,stack);}@Override public int getSlotStackLimit(){return 1;}});}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(inventory,col+row*9+9,44+col*18,215+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(inventory,col,44+col*18,273));
    }
    @Override public boolean canInteractWith(EntityPlayer player){return tile.usable(player);}
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){
        if(index<0||index>=inventorySlots.size())return ItemStack.EMPTY;
        Slot slot=inventorySlots.get(index);if(!slot.getHasStack())return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),original=stack.copy();
        if(index<3){if(!mergeItemStack(stack,3,39,true))return ItemStack.EMPTY;}
        else{boolean moved=false;for(int i=0;i<3;i++)if(tile.isItemValidForSlot(i,stack)&&mergeItemStack(stack,i,i+1,false)){moved=true;break;}if(!moved)return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();
        if(original.getCount()==stack.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);tile.changed();return original;
    }
}
