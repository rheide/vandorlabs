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
    public GuiTokenSlot(ContainerTokenSlot container){super(container);tile=container.tile;xSize=308;ySize=224;}
    @Override public void initGui(){
        xSize=Math.min(308,width-12);ySize=Math.min(224,height-12);super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);buttonList.clear();
        buttonList.add(new GuiButton(1,guiLeft+12,guiTop+34,xSize-24,20,""));
        channels=new GuiTextField(2,fontRenderer,guiLeft+74,guiTop+64,xSize-86,18);channels.setMaxStringLength(256);channels.setText(tile.getRedstoneChannels().toString());
        for(int i=0;i<7;i++){inventorySlots.inventorySlots.get(i).xPos=12+i*(xSize-24)/7;inventorySlots.inventorySlots.get(i).yPos=102;}
        for(int i=7;i<inventorySlots.inventorySlots.size();i++){inventorySlots.inventorySlots.get(i).xPos=(xSize-162)/2+(i-7)%9*18;inventorySlots.inventorySlots.get(i).yPos=i<34?130+(i-7)/9*18:188;}
        labels();
    }
    private void labels(){buttonList.get(0).displayString=TileEntityTokenSlot.behaviorName(tile.behavior);}
    private void save(){if(com.vandorlabs.redstone.ChannelList.parse(channels.getText())==null){channels.setTextColor(0xff7777);return;}channels.setTextColor(0xffffff);com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageTokenSlot(inventorySlots.windowId,tile.behavior,false,tile.effectiveAllowed(),channels.getText()));}
    @Override protected void actionPerformed(GuiButton button){tile.behavior=GuiOptionCycle.next(tile.behavior,4);save();labels();}
    @Override protected void keyTyped(char ch,int key)throws IOException{if(channels.textboxKeyTyped(ch,key))save();else super.keyTyped(ch,key);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1))return;channels.mouseClicked(x,y,button);super.mouseClicked(x,y,button);}
    @Override public void updateScreen(){channels.updateCursorCounter();labels();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mx,int my){
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xff304858);
        for(net.minecraft.inventory.Slot slot:inventorySlots.inventorySlots){int color=slot.slotNumber<7&&(tile.effectiveAllowed()&(1<<slot.slotNumber))!=0?0xff45765a:0xff56616a;drawRect(guiLeft+slot.xPos-1,guiTop+slot.yPos-1,guiLeft+slot.xPos+17,guiTop+slot.yPos+17,color);}
    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(tile.getDisplayName().getUnformattedText(),12,10,0xffffff);fontRenderer.drawString("Channels",12,69,0xffffff);fontRenderer.drawString("Accepted designs",12,90,0xffffff);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);GlStateManager.disableLighting();GlStateManager.disableDepth();channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
