package com.vandorlabs.copier.client;

import com.vandorlabs.copier.*;
import com.vandorlabs.client.GuiOptionCycle;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import java.io.IOException;

public final class GuiCopyController extends GuiContainer {
    private final CopyControllerTile tile;
    private GuiTextField height,increment,channels;
    private int mode;
    private String error="";
    public GuiCopyController(CopyControllerContainer container){super(container);tile=container.tile;mode=tile.triggerMode;xSize=340;ySize=228;}
    @Override public void initGui(){xSize=Math.min(340,width-12);super.initGui();buttonList.clear();height=new GuiTextField(0,fontRenderer,guiLeft+64,guiTop+28,44,18);height.setMaxStringLength(2);height.setText(Integer.toString(tile.height));increment=new GuiTextField(1,fontRenderer,guiLeft+xSize-76,guiTop+28,64,18);increment.setMaxStringLength(6);increment.setText(Integer.toString(tile.channelIncrement));channels=new GuiTextField(2,fontRenderer,guiLeft+12,guiTop+124,xSize-24,18);channels.setMaxStringLength(ChannelList.MAX_TEXT_LENGTH);channels.setText(tile.channels.toString());buttonList.add(new GuiButton(0,guiLeft+12,guiTop+84,xSize-24,20,""));buttonList.add(new GuiButton(1,guiLeft+12,guiTop+150,(xSize-30)/2,20,"Copy"));buttonList.add(new GuiButton(2,guiLeft+18+(xSize-30)/2,guiTop+150,(xSize-30)/2,20,"Check areas"));buttonList.add(new GuiButton(3,guiLeft+12,guiTop+200,xSize-24,20,"Done"));labels();org.lwjgl.input.Keyboard.enableRepeatEvents(true);}
    private void labels(){buttonList.get(0).displayString="Copy trigger: "+new String[]{"Manual","ON edge","OFF edge"}[mode];for(GuiButton button:buttonList)if(button.id!=3)button.enabled=!tile.busy();height.setEnabled(!tile.busy());increment.setEnabled(!tile.busy());channels.setEnabled(!tile.busy());}
    private void submit(int operation){try{int h=Integer.parseInt(height.getText()),delta=Integer.parseInt(increment.getText());ChannelList list=ChannelList.parse(channels.getText());if(h<1||h>16||delta < -65534||delta > 65534||list==null)throw new IllegalArgumentException();error="";PacketHandler.INSTANCE.sendToServer(new CopyMessage(inventorySlots.windowId,operation,h,delta,mode,list));}catch(IllegalArgumentException invalid){error="Height 1-16; channel change -65534 to 65534";}}
    @Override protected void actionPerformed(GuiButton button){if(button.id==3){mc.player.closeScreen();return;}if(button.id==0){mode=GuiOptionCycle.next(mode,3);submit(0);labels();}else submit(button.id);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Copy Controller",12,12,0xffffff);fontRenderer.drawString("Height",12,33,0xcdd7e4);fontRenderer.drawString("Channel change",xSize-174,33,0xcdd7e4);fontRenderer.drawString("Input: left     Output: right     Chest: behind",12,55,0xcdd7e4);String pads=tile.input==null||tile.output==null?"Two matching square surfaces required":tile.input.size+" x "+tile.input.size+" surfaces; complete properties copied";fontRenderer.drawString(pads,12,69,0xcdd7e4);fontRenderer.drawString("Trigger channels (0 = local redstone)",12,111,0xcdd7e4);String status=!error.isEmpty()?error:tile.error.isEmpty()?tile.status:tile.error;java.util.List<String> lines=fontRenderer.listFormattedStringToWidth(status,xSize-24);for(int i=0;i<Math.min(2,lines.size());i++)fontRenderer.drawString(lines.get(i),12,177+i*9,error.isEmpty()&&tile.error.isEmpty()?0xcdd7e4:0xff8888);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();height.drawTextBox();increment.drawTextBox();channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0))return;super.mouseClicked(x,y,button);height.mouseClicked(x,y,button);increment.mouseClicked(x,y,button);channels.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char c,int key)throws IOException{if(height.textboxKeyTyped(c,key)||increment.textboxKeyTyped(c,key)||channels.textboxKeyTyped(c,key))submit(0);else super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();height.updateCursorCounter();increment.updateCursorCounter();channels.updateCursorCounter();labels();}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
