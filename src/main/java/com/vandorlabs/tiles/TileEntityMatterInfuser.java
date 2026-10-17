package com.vandorlabs.tiles;

import com.vandorlabs.items.ModItems;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

/** Solar furnace: redstone supplies catalyst energy only while processing in sunlight. */
public class TileEntityMatterInfuser extends TileEntityFurnace {
    public static int fuelTicks(ItemStack stack){
        if(stack.isEmpty())return 0;
        return stack.getItem()==Items.REDSTONE?33:stack.getItem()==Item.getItemFromBlock(Blocks.REDSTONE_BLOCK)?300:0;
    }
    public boolean hasSunlight(){return world!=null && world.isDaytime() && world.canSeeSky(pos.up()) && !world.isRainingAt(pos.up());}
    public boolean canProcess(){
        ItemStack input=getStackInSlot(0),output=getStackInSlot(2);
        return !input.isEmpty() && input.getItem()==Items.IRON_INGOT && (output.isEmpty() || output.getItem()==ModItems.PROGRAMMABLE_MATTER_INGOT && output.getCount()<Math.min(output.getMaxStackSize(),getInventoryStackLimit()));
    }
    @Override public int getCookTime(ItemStack input){return 200;}
    @Override public String getName(){return "tile.vandorlabs.programmable_matter_infuser.name";}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){return slot==0?stack.getItem()==Items.IRON_INGOT:slot==1 && fuelTicks(stack)>0;}
    @Override public void update(){
        if(world==null || world.isRemote)return;
        setField(3,200);
        if(!canProcess()){if(getField(2)!=0){setField(2,0);markDirty();}return;}
        if(!hasSunlight())return;
        int remaining=getField(0);
        if(remaining<=0){
            ItemStack fuel=getStackInSlot(1);remaining=fuelTicks(fuel);
            if(remaining==0)return;
            fuel.shrink(1);setField(1,remaining);
        }
        setField(0,remaining-1);
        int progress=getField(2)+1;
        if(progress>=200){smeltItem();progress=0;}
        setField(2,progress);markDirty();
    }
    @Override public void smeltItem(){
        if(!canProcess())return;
        getStackInSlot(0).shrink(1);
        ItemStack output=getStackInSlot(2);
        if(output.isEmpty())setInventorySlotContents(2,new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT));else output.grow(1);
    }
}
