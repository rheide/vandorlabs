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
        VehicleTaxiRuntimeChecks.button(mc,2);require(mc.currentScreen instanceof GuiCustomTexture,"Autopilot reuses Custom Texture inventory picker");
        GuiScreen picker=mc.currentScreen;int left=(picker.width-252)/2,top=(picker.height-198)/2;
        ItemStack initial=((ItemStack)field(picker,"sample")).copy();
        mouse(picker,"mouseClicked","func_73864_a",left+45+18+8,top+79+8,0);
        require(ItemStack.areItemStacksEqual(initial,(ItemStack)field(picker,"sample")),"non-block inventory items cannot replace route marker");
        mouse(picker,"mouseClicked","func_73864_a",left+45+8,top+79+8,0);
        VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_autopilot_inventory_picker.png");
        mouse(picker,"mouseReleased","func_146286_b",left+126,top+45,0);
        require(mc.currentScreen==parent,"dragging inventory block into sample returns to Autopilot");
        require(field(parent,"markerId").equals("minecraft:wool")&&((Integer)field(parent,"markerMeta"))==14,"selection retains exact block and color metadata");
        require(((GuiTextField)field(parent,"channels")).getText().equals("28831"),"returning from picker preserves unsaved channels");
        VehicleTaxiRuntimeChecks.button(mc,2);picker=mc.currentScreen;left=(picker.width-252)/2;top=(picker.height-198)/2;
        mouse(picker,"mouseClicked","func_73864_a",left+45+36+8,top+79+8,0);VehicleTaxiRuntimeChecks.button(mc,1);
        require(mc.currentScreen==parent&&field(parent,"markerId").equals("minecraft:wool")&&((Integer)field(parent,"markerMeta"))==14,"Back cancels replacement without changing selected route block");
        for(int i=0;i<36;i++)require(ItemStack.areItemStacksEqual(before[i],mc.player.inventory.getStackInSlot(i)),"sampling never moves or consumes inventory slot "+i);
        VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_autopilot_route_block.png");
        System.out.println("[vandorlabs][reprolab] autopilot-inventory-picker PASS shared-custom-texture-grid actual-mouse drag metadata invalid-item Back draft-preserved non-consuming");
    }
}
