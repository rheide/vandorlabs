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
    private GuiTextField channels;private boolean loaded;
    public GuiTransmitterItem(ContainerTransmitterItem container){super(container);xSize=280;ySize=100;}
    @Override public void initGui(){super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);channels=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+36,xSize-24,18);ChannelFields.configure(channels);channels.setText(ItemRedstoneTransmitter.channels(((ContainerTransmitterItem)inventorySlots).editorStack()).toString());channels.setFocused(true);channels.setEnabled(false);loaded=false;buttonList.add(new GuiButton(1,guiLeft+12,guiTop+66,xSize-24,20,"Done"));}
    private void save(){if(!loaded)return;ChannelList list=ChannelFields.parse(channels);if(list!=null){PacketHandler.INSTANCE.sendToServer(MessageTransmitterSlot.save(inventorySlots.windowId,list));mc.player.closeScreen();}}
    @Override protected void actionPerformed(GuiButton button){save();}
    @Override protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){save();return;}if(!channels.textboxKeyTyped(c,key))super.keyTyped(c,key);}
    @Override protected void mouseClicked(int x,int y,int b)throws IOException{channels.mouseClicked(x,y,b);super.mouseClicked(x,y,b);}
    @Override public void updateScreen(){super.updateScreen();ContainerTransmitterItem container=(ContainerTransmitterItem)inventorySlots;if(!loaded&&container.synchronizedStack){channels.setText(ItemRedstoneTransmitter.channels(container.editorStack()).toString());channels.setEnabled(true);loaded=true;}for(GuiButton button:buttonList)button.enabled=loaded;channels.updateCursorCounter();}
    @Override public void onGuiClosed(){super.onGuiClosed();Keyboard.enableRepeatEvents(false);}
    @Override protected void drawGuiContainerBackgroundLayer(float p,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff101012);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xff304858);DialogHeader.logo(guiLeft,guiTop,xSize);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Transmitter Channels",12,8,0xffffff);}
    @Override public void drawScreen(int x,int y,float p){drawDefaultBackground();super.drawScreen(x,y,p);channels.drawTextBox();}
}
