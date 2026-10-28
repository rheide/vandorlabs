package com.vandorlabs.client;

import com.vandorlabs.coupler.*;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;

/** Standard compact channel dialog; right click cycles the lock behavior in reverse. */
public final class GuiCoupler extends GuiContainer {
    private final TileCoupler tile;private GuiTextField channels;private boolean inverted;
    public GuiCoupler(ContainerCoupler container){super(container);tile=container.tile;inverted=tile.inverted();xSize=260;ySize=198;}
    @Override public void initGui(){String value=channels==null?tile.getRedstoneChannels().toString():channels.getText();super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);channels=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+72,xSize-24,18);channels.setMaxStringLength(256);channels.setText(value);buttonList.clear();GuiButton tab=new DialogTabButton(100,guiLeft+12,guiTop+32,112,20,"Redstone");tab.enabled=false;buttonList.add(tab);buttonList.add(new GuiButton(1,guiLeft+12,guiTop+102,xSize-24,20,"Lock when: "+(inverted?"Off":"On")));buttonList.add(new GuiButton(2,guiLeft+12,guiTop+168,xSize-24,20,"Done"));}
    private void save(){com.vandorlabs.redstone.ChannelList list=com.vandorlabs.redstone.ChannelList.parse(channels.getText());channels.setTextColor(list==null?0xff7777:0xe0e0e0);if(list!=null)com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new MessageCoupler(inventorySlots.windowId,list.toString(),inverted));}
    @Override protected void actionPerformed(GuiButton button){if(button.id==1){inverted=!inverted;save();initGui();}else if(button.id==2){save();mc.player.closeScreen();}}
    @Override protected void mouseClicked(int x,int y,int button)throws java.io.IOException{if(button==1)for(GuiButton option:buttonList)if(option.id==1&&option.mousePressed(mc,x,y)){actionPerformed(option);return;}channels.mouseClicked(x,y,button);super.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char character,int key)throws java.io.IOException{if(channels.textboxKeyTyped(character,key))save();else super.keyTyped(character,key);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xff304858);DialogHeader.logo(guiLeft,guiTop,xSize);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Vehicle Coupler",12,10,0xffffff);fontRenderer.drawString("Input channels (0 = none)",12,60,0xffffff);java.util.List<String> status=fontRenderer.listFormattedStringToWidth(tile.status(),xSize-24);for(int i=0;i<Math.min(2,status.size());i++)fontRenderer.drawString(status.get(i),12,130+i*9,0xcdd7e4);}
    @Override public void drawScreen(int x,int y,float partial){net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();channels.drawTextBox();}
    @Override public void onGuiClosed(){if(channels!=null)save();org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
