package com.vandorlabs.client;

import com.vandorlabs.items.ItemRedstoneTransmitter;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

/** Modal channel selection keeps the original authenticated block window and its draft settings. */
final class GuiHostedTransmitterEditor extends GuiScreen {
    private final GuiContainer parent;private final int selector;private final ItemStack before;private GuiTextField channels;private int left,top;
    GuiHostedTransmitterEditor(GuiContainer parent,int selector,ItemStack before){this.parent=parent;this.selector=selector;this.before=before.copy();}
    @Override public void initGui(){left=(width-280)/2;top=(height-104)/2;buttonList.clear();Keyboard.enableRepeatEvents(true);channels=new GuiTextField(0,fontRenderer,left+12,top+38,256,18);ChannelFields.configure(channels);channels.setText(ItemRedstoneTransmitter.channels(before).toString());channels.setFocused(true);buttonList.add(new GuiButton(1,left+12,top+70,256,20,"Done"));}
    private void save(){ChannelList list=ChannelFields.parse(channels);if(list==null)return;PacketHandler.INSTANCE.sendToServer(MessageTransmitterSlot.hosted(parent.inventorySlots.windowId,selector,before,list));mc.displayGuiScreen(parent);}
    @Override protected void actionPerformed(GuiButton button){save();}
    @Override protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){save();return;}channels.textboxKeyTyped(c,key);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{channels.mouseClicked(x,y,button);super.mouseClicked(x,y,button);}
    @Override public void updateScreen(){channels.updateCursorCounter();if(mc.player==null||mc.player.openContainer!=parent.inventorySlots)mc.displayGuiScreen(null);}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void drawScreen(int x,int y,float partial){parent.drawScreen(-10000,-10000,partial);drawRect(0,0,width,height,0x88000000);drawRect(left,top,left+280,top+104,0xff101012);drawRect(left,top,left+280,top+24,0xff304858);DialogHeader.logo(left,top,280);fontRenderer.drawString("Transmitter Channels",left+12,top+8,0xffffff);super.drawScreen(x,y,partial);channels.drawTextBox();}
}
