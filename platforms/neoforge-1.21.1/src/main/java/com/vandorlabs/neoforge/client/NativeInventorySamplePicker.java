package com.vandorlabs.neoforge.client;

import com.vandorlabs.ui.InventorySampleLayout;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Non-consuming native adapter for the same inline inventory grid used by legacy dialogs. */
public final class NativeInventorySamplePicker {
    public final InventorySampleLayout layout;
    private final Consumer<ItemStack> chosen;
    private final Runnable catalog;
    private int scroll;
    public NativeInventorySamplePicker(int x,int y,int width,int height,Consumer<ItemStack> chosen,Runnable catalog){layout=new InventorySampleLayout(x,y,width,height);this.chosen=chosen;this.catalog=catalog;}
    public int scroll(){return scroll;}
    public void restoreScroll(int value){scroll=Math.max(0,Math.min(layout.maxScroll(),value));}
    public boolean click(int x,int y,int button){
        int action=layout.clickAction(x,y,button,scroll,true);if(action==InventorySampleLayout.OUTSIDE)return false;
        if(action==InventorySampleLayout.BACK){catalog.run();return true;}
        if(action>=0){var sample=Minecraft.getInstance().player.getInventory().getItem(action);if(NativeBlockMaterials.sample(sample)>=0)chosen.accept(sample.copy());}
        return true;
    }
    public boolean wheel(int x,int y,int delta){if(!layout.acceptsWheel(x,y,delta))return false;scroll=layout.scrollAfterWheel(scroll,delta);return true;}
    public void draw(GuiGraphics graphics,int mouseX,int mouseY){
        var mc=Minecraft.getInstance();graphics.fill(layout.x,layout.y,layout.x+layout.width,layout.y+layout.height,0xFF101820);
        graphics.drawString(mc.font,mc.font.plainSubstrByWidth(layout.headerLabel(true),layout.width-4),layout.x+2,layout.y+3,0xFFA4D4E8,false);
        int hovered=layout.slotAt(mouseX,mouseY,scroll);
        for(int i=0;i<layout.columns()*layout.visibleRows();i++){
            int index=layout.visibleSlot(i,scroll);if(index>=layout.slots)break;int x=layout.cellX(i),y=layout.cellY(i);
            graphics.fill(x,y,x+17,y+17,index==hovered?0xFF607C8C:0xFF354958);graphics.renderItem(mc.player.getInventory().getItem(index),x+1,y+1);
        }
        if(hovered>=0){ItemStack item=mc.player.getInventory().getItem(hovered);if(!item.isEmpty())graphics.renderTooltip(mc.font,item,mouseX,mouseY);}
    }
}
