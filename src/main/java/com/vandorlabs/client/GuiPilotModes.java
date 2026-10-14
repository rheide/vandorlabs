package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityPilotSeat;
import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.network.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import java.io.IOException;

public final class GuiPilotModes extends GuiContainer {
    private final TileEntityPilotSeat seat;private final GuiTextField[] fields=new GuiTextField[3];
    public GuiPilotModes(TileEntityPilotSeat seat){super(new ContainerRedstoneChannel(seat));this.seat=seat;xSize=280;ySize=175;}
    public void initGui(){super.initGui();buttonList.clear();for(int i=0;i<3;i++){fields[i]=new GuiTextField(i,fontRenderer,guiLeft+90,guiTop+36+i*26,174,18);ChannelFields.configure(fields[i]);fields[i].setText(seat.modeChannels(i).toString());}buttonList.add(new GuiButton(4,guiLeft+14,guiTop+143,252,20,"Done"));org.lwjgl.input.Keyboard.enableRepeatEvents(true);}
    protected void actionPerformed(GuiButton b){ChannelList[] values=new ChannelList[3];for(int i=0;i<3;i++){values[i]=ChannelFields.parse(fields[i]);if(values[i]==null)return;}PacketHandler.INSTANCE.sendToServer(new MessagePilotModes(seat.getPos(),values));mc.player.closeScreen();}
    protected void keyTyped(char c,int key)throws IOException{boolean used=false;for(GuiTextField f:fields)used|=f.textboxKeyTyped(c,key);if(!used)super.keyTyped(c,key);}
    protected void mouseClicked(int x,int y,int b)throws IOException{super.mouseClicked(x,y,b);for(GuiTextField f:fields)f.mouseClicked(x,y,b);}
    public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF17212B);}
    protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Pilot Seat: mode channels",14,12,0xFFFFFF);String[] names={"Park","Taxi","Hover"};for(int i=0;i<3;i++)fontRenderer.drawString(names[i],14,41+i*26,0xDAE8F0);fontRenderer.drawString("0 = disabled; rising power selects mode",14,122,0xAABBCC);}
    public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();for(GuiTextField f:fields)f.drawTextBox();}
}
