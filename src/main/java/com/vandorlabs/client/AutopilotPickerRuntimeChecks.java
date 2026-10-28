package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import java.io.File;
import java.lang.reflect.Method;

/** Exercise the shared non-consuming inventory sample picker through actual mouse handlers. */
final class AutopilotPickerRuntimeChecks {
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("autopilot inventory picker: "+text);}
    private static Object field(Object object,String name)throws Exception{java.lang.reflect.Field value=object.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(object);}
    private static void mouse(GuiScreen gui,String name,String mapped,int x,int y,int button)throws Exception{
        Method method=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(GuiScreen.class,name,mapped,int.class,int.class,int.class);method.invoke(gui,x,y,button);
    }
    static void check(Minecraft mc,File output)throws Exception{
        GuiAutopilot parent=(GuiAutopilot)mc.currentScreen;ItemStack[] before=new ItemStack[36];for(int i=0;i<36;i++)before[i]=mc.player.inventory.getStackInSlot(i).copy();
        require(before[9].getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.WOOL)&&before[9].getMetadata()==14,"inventory contains a colored route block");
        ((GuiTextField)field(parent,"channels")).setText("28831");
        VehicleTaxiRuntimeChecks.button(mc,2);require(mc.currentScreen==parent,"route inventory is an inline top-level tab");
        InventorySamplePicker picker=(InventorySamplePicker)field(parent,"routePicker");String initial=(String)field(parent,"markerId");
        mouse(parent,"mouseClicked","func_73864_a",picker.x+18+8,picker.y+16+18+8,0);
        require(field(parent,"markerId").equals(initial),"non-block items cannot replace route marker");
        mouse(parent,"mouseClicked","func_73864_a",picker.x+8,picker.y+16+18+8,0);
        VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_autopilot_inventory_picker.png");
        require(mc.currentScreen==parent,"sampling preserves parent dialog");
        require(field(parent,"markerId").equals("minecraft:wool")&&((Integer)field(parent,"markerMeta"))==14,"selection retains exact block and color metadata");
        require(((GuiTextField)field(parent,"channels")).getText().equals("28831"),"tab switching preserves unsaved channels");
        VehicleTaxiRuntimeChecks.button(mc,100);require(mc.currentScreen==parent,"General is a top-level tab in the same window");
        for(int i=0;i<36;i++)require(ItemStack.areItemStacksEqual(before[i],mc.player.inventory.getStackInSlot(i)),"sampling never moves or consumes inventory slot "+i);
        VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_autopilot_route_block.png");
        System.out.println("[vandorlabs][reprolab] autopilot-inventory-picker PASS inline-inventory-grid actual-mouse metadata invalid-item top-level-tabs draft-preserved non-consuming");
    }
}
