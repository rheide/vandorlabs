package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import java.io.IOException;

public final class GuiTokenSlot extends GuiContainer {
    private final TileEntityTokenSlot tile;
    private GuiTextField channels;
    public GuiTokenSlot(ContainerTokenSlot container){super(container);tile=container.tile;xSize=308;ySize=248;}
    @Override public void initGui(){
        xSize=Math.min(308,width-12);ySize=Math.min(248,height-12);super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);buttonList.clear();
        buttonList.add(new GuiButton(0,guiLeft+42,guiTop+36,xSize-54,20,""));
        buttonList.add(new GuiButton(1,guiLeft+12,guiTop+60,xSize-24,20,""));
        channels=new GuiTextField(2,fontRenderer,guiLeft+74,guiTop+85,xSize-86,18);channels.setMaxStringLength(256);channels.setText(tile.getRedstoneChannels().toString());
        inventorySlots.inventorySlots.get(0).yPos=38;
        for(int i=1;i<=7;i++){inventorySlots.inventorySlots.get(i).xPos=12+(i-1)*(xSize-24)/7;inventorySlots.inventorySlots.get(i).yPos=123;}
        for(int i=8;i<inventorySlots.inventorySlots.size();i++){inventorySlots.inventorySlots.get(i).xPos=(xSize-162)/2+(i-8)%9*18;inventorySlots.inventorySlots.get(i).yPos=i<35?148+(i-8)/9*18:206;}
        labels();
    }
    private void labels(){buttonList.get(0).displayString="Any type of "+(tile.chip()?"chip":"card")+": "+(tile.any?"Yes":"No");buttonList.get(1).displayString=tile.behavior==0?"Keep inserted: continuous signal":tile.behavior==1?"Keep inserted: one-second pulse":"Insert to toggle, then return item";}
    private void save(){if(com.vandorlabs.redstone.ChannelList.parse(channels.getText())==null){channels.setTextColor(0xff7777);return;}channels.setTextColor(0xffffff);com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageTokenSlot(inventorySlots.windowId,tile.behavior,tile.any,tile.allowed,channels.getText()));}
    @Override protected void actionPerformed(GuiButton button){if(button.id==0)tile.any=!tile.any;else if(button.id==1)tile.behavior=(tile.behavior+(isShiftKeyDown()?2:1))%3;save();labels();}
    @Override protected void keyTyped(char ch,int key)throws IOException{if(channels.textboxKeyTyped(ch,key))save();else super.keyTyped(ch,key);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{channels.mouseClicked(x,y,button);super.mouseClicked(x,y,button);}
    @Override public void updateScreen(){channels.updateCursorCounter();labels();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mx,int my){
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xff304858);
        for(net.minecraft.inventory.Slot slot:inventorySlots.inventorySlots){int color=slot.slotNumber>=1&&slot.slotNumber<=7&&(tile.any||(tile.allowed&(1<<(slot.slotNumber-1)))!=0)?0xff45765a:0xff56616a;drawRect(guiLeft+slot.xPos-1,guiTop+slot.yPos-1,guiLeft+slot.xPos+17,guiTop+slot.yPos+17,color);}
    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(tile.getDisplayName().getUnformattedText(),12,10,0xffffff);fontRenderer.drawString("Inserted",12,28,0xffffff);fontRenderer.drawString("Channels",12,90,0xffffff);fontRenderer.drawString("Allowed designs: click or place an item here",12,110,0xffffff);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);GlStateManager.disableLighting();GlStateManager.disableDepth();channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
