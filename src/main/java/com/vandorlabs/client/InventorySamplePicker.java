package com.vandorlabs.client;

import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.item.ItemStack;

/** Non-consuming inventory samples drawn inside the owning configuration page. */
final class InventorySamplePicker {
    final int x,y,width,height;private final Predicate<ItemStack> accepts;private final Consumer<ItemStack> chosen;private final Runnable catalog;
    private int scroll;private final int slots;
    InventorySamplePicker(int x,int y,int width,int height,Predicate<ItemStack> accepts,Consumer<ItemStack> chosen,Runnable catalog){this(x,y,width,height,accepts,chosen,catalog,36);}
    private InventorySamplePicker(int x,int y,int width,int height,Predicate<ItemStack> accepts,Consumer<ItemStack> chosen,Runnable catalog,int slots){this.x=x;this.y=y;this.width=width;this.height=height;this.accepts=accepts;this.chosen=chosen;this.catalog=catalog;this.slots=slots;}
    static InventorySamplePicker hotbar(int x,int y,Predicate<ItemStack> accepts,Consumer<ItemStack> chosen){return new InventorySamplePicker(x,y,9*18,34,accepts,chosen,null,9);}
    private int columns(){return Math.max(1,Math.min(9,width/18));}
    private int visible(){return Math.max(1,(height-16)/18);}
    private int maxScroll(){return Math.max(0,(slots+columns()-1)/columns()-visible());}
    private int index(int mx,int my){if(mx<x||mx>=x+columns()*18||my<y+16||my>=y+16+visible()*18)return -1;int slot=(scroll+(my-y-16)/18)*columns()+(mx-x)/18;return slot<slots?slot:-1;}
    boolean click(int mx,int my,int button){if(mx<x||mx>=x+width||my<y||my>=y+height)return false;if(button==0&&my<y+14&&catalog!=null){catalog.run();return true;}int index=index(mx,my);if(button==0&&index>=0){ItemStack sample=Minecraft.getMinecraft().player.inventory.getStackInSlot(index);if(accepts.test(sample))chosen.accept(sample.copy());}return true;}
    boolean wheel(int mx,int my,int delta){if(delta==0||mx<x||mx>=x+width||my<y||my>=y+height)return false;scroll=Math.max(0,Math.min(maxScroll(),scroll+(delta>0?-1:1)));return true;}
    void draw(int mx,int my){Minecraft mc=Minecraft.getMinecraft();Gui.drawRect(x,y,x+width,y+height,0xff101820);mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(catalog==null?(slots==9?"Choose a hotbar block":"Choose an inventory item"):"< Catalog / inventory",width-4),x+2,y+3,0xffa4d4e8);
        int hovered=index(mx,my);RenderHelper.enableGUIStandardItemLighting();
        for(int i=0;i<columns()*visible();i++){int index=scroll*columns()+i;if(index>=slots)break;int xx=x+i%columns()*18,yy=y+16+i/columns()*18;Gui.drawRect(xx,yy,xx+17,yy+17,index==hovered?0xff607c8c:0xff354958);ItemStack item=mc.player.inventory.getStackInSlot(index);mc.getRenderItem().renderItemAndEffectIntoGUI(item,xx+1,yy+1);}
        RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.disableLighting();
        if(hovered>=0){ItemStack item=mc.player.inventory.getStackInSlot(hovered);if(!item.isEmpty())net.minecraftforge.fml.client.config.GuiUtils.drawHoveringText(item.getTooltip(mc.player,mc.gameSettings.advancedItemTooltips?net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED:net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL),mx,my,new ScaledResolution(mc).getScaledWidth(),new ScaledResolution(mc).getScaledHeight(),-1,mc.fontRenderer);}
    }
}
