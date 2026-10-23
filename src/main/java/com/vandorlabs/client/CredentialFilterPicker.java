package com.vandorlabs.client;

import com.vandorlabs.items.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import java.util.function.IntConsumer;

/** Shared door/mount filter: a wildcard label and one inventory-sampled company item. */
final class CredentialFilterPicker {
    static ItemStack sample(boolean chip,int mask){return Integer.bitCount(mask)==1&&mask>1&&mask<=64?new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,1,Integer.numberOfTrailingZeros(mask)):ItemStack.EMPTY;}
    static String label(boolean chip,int mask){if(mask==0)return chip?"No chip":"No card";ItemStack item=sample(chip,mask);if(item.isEmpty())return chip?"Any chip":"Any card";String company=ItemDesignToken.DESIGNS[item.getMetadata()];return Character.toUpperCase(company.charAt(0))+company.substring(1);}
    static boolean click(GuiScreen parent,int x,int y,int button,int left,int top,boolean chip,int mask,IntConsumer selected){
        if((button!=0&&button!=1)||x<left||x>=left+154||y<top||y>=top+18)return false;
        if(button==1||x<left+128){selected.accept(127);return true;}
        Minecraft.getMinecraft().displayGuiScreen(new GuiCustomTexture(parent,chip?"Accepted company chip":"Accepted company card","Use company",sample(chip,mask),
                stack->!stack.isEmpty()&&stack.getItem()==(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD)&&stack.getMetadata()>=0&&stack.getMetadata()<7,
                stack->selected.accept(stack.getMetadata()==0?127:1<<stack.getMetadata())));return true;
    }
    static void draw(Minecraft mc,FontRenderer font,int left,int top,boolean chip,int mask){
        int x=left+136;Gui.drawRect(x-1,top-1,x+17,top+17,0xff647480);Gui.drawRect(x,top,x+16,top+16,0xff28343e);
        ItemStack item=sample(chip,mask);Gui.drawRect(left,top-1,left+128,top+17,item.isEmpty()?0xff45765a:0xff28343e);
        font.drawString(label(chip,mask),left+4,mask==127?top+4:top,0xffdae8f0);
        if(mask!=127)font.drawString("Click to clear",left+4,top+9,0xffaabcc8);
        RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(item,x,top);RenderHelper.disableStandardItemLighting();
    }
}
