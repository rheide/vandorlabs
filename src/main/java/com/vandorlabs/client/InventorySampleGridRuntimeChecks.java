package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Opt-in actual inventory sample clicks; no gameplay packets or inventory ownership transfer. */
final class InventorySampleGridRuntimeChecks {
    private InventorySampleGridRuntimeChecks() { }
    static void run(Minecraft mc){
        ItemStack[] before=new ItemStack[36],contents=new ItemStack[36];for(int i=0;i<36;i++){before[i]=mc.player.inventory.getStackInSlot(i);contents[i]=before[i].copy();}
        net.minecraft.client.gui.GuiScreen screen=mc.currentScreen;net.minecraft.inventory.Container container=mc.player.openContainer;
        try{
            mc.player.inventory.setInventorySlotContents(0,new ItemStack(Blocks.WOOL,3,14));
            mc.player.inventory.setInventorySlotContents(1,new ItemStack(Items.STICK,5));
            mc.player.inventory.setInventorySlotContents(8,new ItemStack(Blocks.GLASS,2));
            mc.player.inventory.setInventorySlotContents(9,new ItemStack(Blocks.STONE,2));
            int[] chosen={0},back={0};ItemStack[] sample={ItemStack.EMPTY};
            InventorySamplePicker picker=new InventorySamplePicker(10,10,162,52,s->s.getItem() instanceof net.minecraft.item.ItemBlock,s->{chosen[0]++;sample[0]=s;},()->back[0]++);
            require(picker.click(18,34,0)&&chosen[0]==1&&sample[0].getItem()==Item.getItemFromBlock(Blocks.WOOL)&&sample[0].getMetadata()==14&&sample[0].getCount()==3,"Actual block metadata/count selection");
            require(sample[0]!=mc.player.inventory.getStackInSlot(0),"Callback borrowed inventory ownership");sample[0].setCount(1);require(mc.player.inventory.getStackInSlot(0).getCount()==3,"Callback copy changed inventory");
            picker.click(36,34,0);picker.click(18,34,1);require(chosen[0]==1,"Invalid item or right click selected sample");
            picker.click(18,24,0);picker.click(18,25,0);require(back[0]==0&&chosen[0]==1,"14-15px header gap dispatched");
            require(picker.click(171,23,0)&&back[0]==1,"Catalog back outer-width hit");
            require(!picker.wheel(172,34,-120)&&!picker.wheel(18,34,0)&&picker.wheel(18,11,-120),"Actual wheel acceptance");
            picker.click(18,34,0);require(chosen[0]==2&&sample[0].getItem()==Item.getItemFromBlock(Blocks.STONE),"Scrolled inventory row did not select slot9");
            int[] hotbarChosen={0};InventorySamplePicker hotbar=InventorySamplePicker.hotbar(10,100,s->s.getItem() instanceof net.minecraft.item.ItemBlock,s->{hotbarChosen[0]++;require(s.getItem()==Item.getItemFromBlock(Blocks.GLASS),"Hotbar final slot changed");});
            require(hotbar.click(162,124,0)&&hotbarChosen[0]==1&&!hotbar.click(18,134,0),"Nine-slot hotbar cannot reach main inventory");
            require(hotbar.wheel(18,101,-120),"Hotbar wheel should consume inside input even with zero scroll");hotbar.click(162,124,0);require(hotbarChosen[0]==2,"Hotbar wheel changed slot mapping");
            require(mc.currentScreen==screen&&mc.player.openContainer==container,"Picker replaced parent dialog/container");
            require(mc.player.inventory.getStackInSlot(0).getCount()==3&&mc.player.inventory.getStackInSlot(1).getCount()==5&&mc.player.inventory.getStackInSlot(8).getCount()==2&&mc.player.inventory.getStackInSlot(9).getCount()==2,"Sampling consumed inventory");
            for(int i=0;i<36;i++)if(i!=0&&i!=1&&i!=8&&i!=9)require(ItemStack.areItemStacksEqual(contents[i],mc.player.inventory.getStackInSlot(i)),"Unrelated inventory slot changed "+i);
            System.out.println("[vandorlabs][reprolab] inventory-sample-grid-live PASS actual36slot/9hotbar clicks metadata copied non-consuming invalid/right-click header14/16gap back and wheel bounds parent retained");
        }finally{for(int i=0;i<36;i++)mc.player.inventory.setInventorySlotContents(i,before[i]);}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Inventory sample grid: "+message);}
}
