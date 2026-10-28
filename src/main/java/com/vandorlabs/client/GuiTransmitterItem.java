package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTransmitterItem;
import com.vandorlabs.items.ItemRedstoneTransmitter;
import com.vandorlabs.network.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

/** Channel selection only; analog/digital behavior belongs to the host block. */
public final class GuiTransmitterItem extends GuiContainer {
    private GuiTextField channels;
    public GuiTransmitterItem(ContainerTransmitterItem container){super(container);xSize=280;ySize=100;}
    @Override public void initGui(){super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);channels=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+36,xSize-24,18);ChannelFields.configure(channels);channels.setText(ItemRedstoneTransmitter.channels(((ContainerTransmitterItem)inventorySlots).stack(mc.player)).toString());channels.setFocused(true);buttonList.add(new GuiButton(1,guiLeft+12,guiTop+66,xSize-24,20,"Done"));}
    private void save(){ChannelList list=ChannelFields.parse(channels);if(list!=null){PacketHandler.INSTANCE.sendToServer(MessageTransmitterSlot.save(inventorySlots.windowId,list));mc.player.closeScreen();}}
    @Override protected void actionPerformed(GuiButton button){save();}
    @Override protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){save();return;}if(!channels.textboxKeyTyped(c,key))super.keyTyped(c,key);}
    @Override protected void mouseClicked(int x,int y,int b)throws IOException{channels.mouseClicked(x,y,b);super.mouseClicked(x,y,b);}
    @Override public void updateScreen(){super.updateScreen();channels.updateCursorCounter();}
    @Override public void onGuiClosed(){super.onGuiClosed();Keyboard.enableRepeatEvents(false);}
    @Override protected void drawGuiContainerBackgroundLayer(float p,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff101012);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xff304858);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Transmitter Channels",12,8,0xffffff);}
    @Override public void drawScreen(int x,int y,float p){drawDefaultBackground();super.drawScreen(x,y,p);channels.drawTextBox();}
}
