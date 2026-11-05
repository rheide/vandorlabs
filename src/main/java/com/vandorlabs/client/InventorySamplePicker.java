package com.vandorlabs.client;

import com.vandorlabs.ui.InventorySampleLayout;

import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.item.ItemStack;

/** Non-consuming inventory samples drawn inside the owning configuration page. */
final class InventorySamplePicker {
    final int x,y,width,height;private final Predicate<ItemStack> accepts;private final Consumer<ItemStack> chosen;private final Runnable catalog;
    private int scroll;private final int slots;private final InventorySampleLayout layout;
    InventorySamplePicker(int x,int y,int width,int height,Predicate<ItemStack> accepts,Consumer<ItemStack> chosen,Runnable catalog){this(x,y,width,height,accepts,chosen,catalog,InventorySampleLayout.INVENTORY_SLOTS);}
    private InventorySamplePicker(int x,int y,int width,int height,Predicate<ItemStack> accepts,Consumer<ItemStack> chosen,Runnable catalog,int slots){this.x=x;this.y=y;this.width=width;this.height=height;this.accepts=accepts;this.chosen=chosen;this.catalog=catalog;this.slots=slots;this.layout=new InventorySampleLayout(x,y,width,height,slots);}
    static InventorySamplePicker hotbar(int x,int y,Predicate<ItemStack> accepts,Consumer<ItemStack> chosen){return new InventorySamplePicker(x,y,InventorySampleLayout.HOTBAR_WIDTH,InventorySampleLayout.HOTBAR_HEIGHT,accepts,chosen,null,InventorySampleLayout.HOTBAR_SLOTS);}
    private int columns(){return layout.columns();}
    private int visible(){return layout.visibleRows();}
    private int maxScroll(){return layout.maxScroll();}
    private int index(int mx,int my){return layout.slotAt(mx,my,scroll);}
    boolean click(int mx,int my,int button){int action=layout.clickAction(mx,my,button,scroll,catalog!=null);if(action==InventorySampleLayout.OUTSIDE)return false;if(action==InventorySampleLayout.BACK){catalog.run();return true;}if(action>=0){ItemStack sample=Minecraft.getMinecraft().player.inventory.getStackInSlot(action);if(accepts.test(sample))chosen.accept(sample.copy());}return true;}
    boolean wheel(int mx,int my,int delta){if(!layout.acceptsWheel(mx,my,delta))return false;scroll=layout.scrollAfterWheel(scroll,delta);return true;}
    void draw(int mx,int my){Minecraft mc=Minecraft.getMinecraft();Gui.drawRect(x,y,x+width,y+height,0xff101820);mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(layout.headerLabel(catalog!=null),width-4),x+2,y+3,0xffa4d4e8);
        int hovered=index(mx,my);RenderHelper.enableGUIStandardItemLighting();
        for(int i=0;i<columns()*visible();i++){int index=layout.visibleSlot(i,scroll);if(index>=slots)break;int xx=layout.cellX(i),yy=layout.cellY(i);Gui.drawRect(xx,yy,xx+17,yy+17,index==hovered?0xff607c8c:0xff354958);ItemStack item=mc.player.inventory.getStackInSlot(index);mc.getRenderItem().renderItemAndEffectIntoGUI(item,xx+1,yy+1);}
        RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.disableLighting();
        if(hovered>=0){ItemStack item=mc.player.inventory.getStackInSlot(hovered);if(!item.isEmpty())net.minecraftforge.fml.client.config.GuiUtils.drawHoveringText(item.getTooltip(mc.player,mc.gameSettings.advancedItemTooltips?net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED:net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL),mx,my,new ScaledResolution(mc).getScaledWidth(),new ScaledResolution(mc).getScaledHeight(),-1,mc.fontRenderer);}
    }
}
