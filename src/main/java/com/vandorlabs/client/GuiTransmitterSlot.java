package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTransmitterSlot;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;

public final class GuiTransmitterSlot extends GuiContainer {
    public GuiTransmitterSlot(ContainerTransmitterSlot container){super(container);xSize=176;ySize=168;}
    @Override public void initGui(){super.initGui();buttonList.clear();buttonList.add(new GuiButton(1,guiLeft+52,guiTop+38,112,20,"Channels..."));}
    @Override protected void actionPerformed(GuiButton button){if(button.id==1)PacketHandler.INSTANCE.sendToServer(MessageTransmitterSlot.edit(inventorySlots.windowId));}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff101012);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xff304858);drawRect(guiLeft+25,guiTop+39,guiLeft+43,guiTop+57,0xff809098);for(int r=0;r<4;r++)for(int c=0;c<9;c++){int yy=r==3?143:85+r*18;drawRect(guiLeft+7+c*18,guiTop+yy,guiLeft+25+c*18,guiTop+yy+18,0xff40505a);}}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Redstone Transmitter",8,8,0xffffff);fontRenderer.drawString("Insert a transmitter to broadcast",8,66,0xdae8f0);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);renderHoveredToolTip(x,y);}
}
