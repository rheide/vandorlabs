package com.vandorlabs.container;

import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;

public final class ContainerTokenSlot extends Container {
    public final TileEntityTokenSlot tile;
    public ContainerTokenSlot(InventoryPlayer player,TileEntityTokenSlot tile){
        this.tile=tile;
        // Player slots are positioned by the GUI; server coordinates have no gameplay meaning.
        addSlotToContainer(new Slot(tile,0,12,46){@Override public boolean canTakeStack(EntityPlayer owner){return !tile.returning();}});
        InventoryBasic filters=new InventoryBasic("Allowed designs",false,7);
        for(int i=0;i<7;i++){filters.setInventorySlotContents(i,new ItemStack(tile.chip()?com.vandorlabs.items.ModItems.DATA_CHIP:com.vandorlabs.items.ModItems.DATA_CARD,1,i));addSlotToContainer(new Slot(filters,i,12+i*40,136){@Override public boolean isItemValid(ItemStack stack){return false;}@Override public boolean canTakeStack(EntityPlayer player){return false;}});}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col+row*9+9,73+col*18,164+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col,73+col*18,222));
    }
    @Override public boolean canInteractWith(EntityPlayer player){return tile.isUsableByPlayer(player);}
    @Override public ItemStack slotClick(int id,int button,ClickType type,EntityPlayer player){
        if(!canInteractWith(player))return ItemStack.EMPTY;
        if(id==0){
            if(tile.returning())return ItemStack.EMPTY;
            if(tile.behavior==2&&type==ClickType.PICKUP&&!player.inventory.getItemStack().isEmpty()){
                tile.insertToggle(player,player.inventory.getItemStack());return ItemStack.EMPTY;
            }
        }
        if(id>=1&&id<=7){
            if(type!=ClickType.PICKUP)return ItemStack.EMPTY;
            ItemStack carried=player.inventory.getItemStack();int mask=tile.any?0:tile.allowed;
            if(!carried.isEmpty()){if(!tile.accepts(carried))return ItemStack.EMPTY;mask|=1<<carried.getMetadata();}
            else mask^=1<<(id-1);
            tile.configure(false,mask,tile.behavior);return ItemStack.EMPTY;
        }
        return super.slotClick(id,button,type,player);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){
        if(index<0||index>=inventorySlots.size()||index>=1&&index<=7||!canInteractWith(player))return ItemStack.EMPTY;
        Slot slot=inventorySlots.get(index);ItemStack stack=slot.getStack();if(stack.isEmpty())return ItemStack.EMPTY;ItemStack before=stack.copy();
        if(tile.returning())return ItemStack.EMPTY;
        if(index>=8&&tile.behavior==2){if(!tile.insertToggle(player,stack))return ItemStack.EMPTY;slot.onSlotChanged();return before;}
        if(index==0){if(!mergeItemStack(stack,8,inventorySlots.size(),true))return ItemStack.EMPTY;}
        else if(!tile.accepts(stack)||!mergeItemStack(stack,0,1,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();slot.onTake(player,stack);tile.refresh();return before;
    }
}
