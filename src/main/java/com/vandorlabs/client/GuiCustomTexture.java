package com.vandorlabs.client;

import com.vandorlabs.tiles.CustomBlockMaterials;
import net.minecraft.client.gui.*;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import java.util.function.IntConsumer;

/** A non-consuming sample slot backed by the player's real inventory. */
final class GuiCustomTexture extends GuiScreen {
    private final GuiScreen parent;
    private final IntConsumer selected;
    private ItemStack sample=ItemStack.EMPTY;
    private int left,top;
    private boolean dragging;
    GuiCustomTexture(GuiScreen parent,IntConsumer selected){this.parent=parent;this.selected=selected;}
    @Override public void initGui(){left=(width-252)/2;top=(height-198)/2;buttonList.clear();buttonList.add(new GuiButton(0,left+12,top+169,110,20,"Use texture"));buttonList.add(new GuiButton(1,left+130,top+169,110,20,"Back"));}
    @Override public boolean doesGuiPauseGame(){return false;}
    private int slot(int x,int y){int col=(x-left-45)/18;if(x<left+45||col<0||col>=9)return -1;if(y>=top+79&&y<top+133)return 9+((y-top-79)/18)*9+col;if(y>=top+141&&y<top+159)return col;return -1;}
    private boolean ghost(int x,int y){return x>=left+116&&x<left+136&&y>=top+35&&y<top+55;}
    private void apply(){int choice=CustomBlockMaterials.choice(sample);if(choice<0)return;selected.accept(choice);mc.displayGuiScreen(parent);}
    @Override protected void actionPerformed(GuiButton button){if(button.id==0)apply();else mc.displayGuiScreen(parent);}
    @Override protected void keyTyped(char c,int key){if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}if(key==Keyboard.KEY_RETURN)apply();}
    @Override protected void mouseClicked(int x,int y,int button) throws java.io.IOException {
        if(button==0){int slot=slot(x,y);if(slot>=0){ItemStack item=mc.player.inventory.getStackInSlot(slot);if(CustomBlockMaterials.block(item)!=null){sample=item.copy();sample.setCount(1);dragging=true;}return;}if(ghost(x,y)&&!mc.player.inventory.getItemStack().isEmpty()){sample=mc.player.inventory.getItemStack().copy();sample.setCount(1);return;}}
        super.mouseClicked(x,y,button);
    }
    @Override protected void mouseReleased(int x,int y,int button){if(dragging&&ghost(x,y)){dragging=false;apply();return;}dragging=false;super.mouseReleased(x,y,button);}
    @Override public void drawScreen(int x,int y,float partial){
        drawDefaultBackground();drawRect(left,top,left+252,top+198,0xFF18242F);
        fontRenderer.drawString("Custom block / door texture",left+12,top+12,0xFFFFFF);
        fontRenderer.drawString("Drag a block into the sample slot",left+12,top+63,0xCCDDEE);
        drawRect(left+115,top+34,left+137,top+56,0xFF8193A6);drawRect(left+116,top+35,left+136,top+55,0xFF101821);
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        if(!sample.isEmpty())itemRender.renderItemAndEffectIntoGUI(sample,left+118,top+37);
        for(int slot=0;slot<36;slot++){int xx=left+45+(slot%9)*18,yy=top+(slot<9?141:79+(slot/9-1)*18);drawRect(xx,yy,xx+17,yy+17,0xFF344556);itemRender.renderItemAndEffectIntoGUI(mc.player.inventory.getStackInSlot(slot),xx+1,yy+1);}
        if(dragging&&!sample.isEmpty())itemRender.renderItemAndEffectIntoGUI(sample,x-8,y-8);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        buttonList.get(0).enabled=CustomBlockMaterials.choice(sample)>=0;super.drawScreen(x,y,partial);
    }
}
