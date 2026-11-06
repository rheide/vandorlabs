package com.vandorlabs.client;

import com.vandorlabs.items.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import java.util.function.IntConsumer;

/** Shared door/mount filter: a wildcard label and one inventory-sampled company item. */
final class CredentialFilterPicker {
    static ItemStack sample(boolean chip,int mask){int company=com.vandorlabs.credentials.CredentialCatalog.sampleCompany(mask);return company<0?ItemStack.EMPTY:new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,1,company);}
    static String label(boolean chip,int mask){return com.vandorlabs.credentials.CredentialCatalog.label(chip,mask);}
    static boolean click(GuiScreen parent,int x,int y,int button,int left,int top,boolean chip,int mask,IntConsumer selected){int next=com.vandorlabs.credentials.CredentialCatalog.filterSelection(x,y,button,left,top,mask);if(next==com.vandorlabs.credentials.CredentialCatalog.NO_SELECTION)return false;selected.accept(next);return true;}
    static void draw(Minecraft mc,FontRenderer font,int left,int top,boolean chip,int mask){
        int x=left+136;Gui.drawRect(x-1,top-1,x+17,top+17,0xff647480);Gui.drawRect(x,top,x+16,top+16,0xff28343e);
        ItemStack item=sample(chip,mask);Gui.drawRect(left,top-1,left+128,top+17,item.isEmpty()?0xff45765a:0xff28343e);
        font.drawString(label(chip,mask),left+4,mask==127?top+4:top,0xffdae8f0);
        if(mask!=127)font.drawString("Click to clear",left+4,top+9,0xffaabcc8);
        RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(item,x,top);RenderHelper.disableStandardItemLighting();
    }
}
