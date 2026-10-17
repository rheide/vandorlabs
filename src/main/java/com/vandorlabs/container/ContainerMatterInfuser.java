package com.vandorlabs.container;

import com.vandorlabs.tiles.TileEntityMatterInfuser;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;

public final class ContainerMatterInfuser extends ContainerFurnace {
    public ContainerMatterInfuser(InventoryPlayer player,TileEntityMatterInfuser tile){super(player,tile);Slot fuel=new Slot(tile,1,56,53){@Override public boolean isItemValid(ItemStack stack){return TileEntityMatterInfuser.fuelTicks(stack)>0;}};fuel.slotNumber=1;inventorySlots.set(1,fuel);}
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){
        Slot slot=inventorySlots.get(index);if(slot==null || !slot.getHasStack())return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),original=stack.copy();
        if(index<3){if(!mergeItemStack(stack,3,39,true))return ItemStack.EMPTY;}
        else if(stack.getItem()==net.minecraft.init.Items.IRON_INGOT){if(!mergeItemStack(stack,0,1,false))return ItemStack.EMPTY;}
        else if(TileEntityMatterInfuser.fuelTicks(stack)>0){if(!mergeItemStack(stack,1,2,false))return ItemStack.EMPTY;}
        else if(index<30){if(!mergeItemStack(stack,30,39,false))return ItemStack.EMPTY;}
        else if(!mergeItemStack(stack,3,30,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;
        slot.onTake(player,stack);return original;
    }
}
