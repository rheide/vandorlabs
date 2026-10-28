package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityPilotSeat;
import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.network.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import java.io.IOException;

/** Valid mode-channel edits apply after a short typing pause. */
public final class GuiPilotModes extends GuiContainer {
    private final TileEntityPilotSeat seat;
    private final GuiTextField[] fields=new GuiTextField[3];
    private final java.util.function.Function<ChannelList[],Boolean> sender;
    private final java.util.function.BooleanSupplier busy;
    private final Runnable close;
    private String observed,sent;private int delay;private boolean closing;
    public GuiPilotModes(TileEntityPilotSeat seat){this(seat,null,null,null);}
    GuiPilotModes(TileEntityPilotSeat seat,java.util.function.Function<ChannelList[],Boolean> sender,java.util.function.BooleanSupplier busy,Runnable close){super(new ContainerRedstoneChannel(seat));this.seat=seat;this.sender=sender;this.busy=busy;this.close=close;sent=seat.modeChannels(0)+"|"+seat.modeChannels(1)+"|"+seat.modeChannels(2);}
    public void initGui(){
        String[] draft=new String[3];for(int i=0;i<3;i++)draft[i]=fields[i]==null?seat.modeChannels(i).toString():fields[i].getText();
        xSize=Math.min(280,width-16);ySize=186;super.initGui();buttonList.clear();
        for(int i=0;i<3;i++){fields[i]=new GuiTextField(i,fontRenderer,guiLeft+88,guiTop+40+i*29,xSize-102,18);ChannelFields.configure(fields[i]);fields[i].setText(draft[i]);}
        buttonList.add(new GuiButton(4,guiLeft+(xSize-200)/2,guiTop+158,200,20,"Close"));org.lwjgl.input.Keyboard.enableRepeatEvents(true);observed=textKey();
    }
    private String textKey(){return fields[0].getText()+"|"+fields[1].getText()+"|"+fields[2].getText();}
    private void apply(){
        ChannelList[] values=new ChannelList[3];for(int i=0;i<3;i++){values[i]=ChannelFields.parse(fields[i]);if(values[i]==null)return;}
        String key=values[0]+"|"+values[1]+"|"+values[2];if(key.equals(sent))return;
        if(sender!=null){if(!sender.apply(values))return;}else PacketHandler.INSTANCE.sendToServer(new MessagePilotModes(seat.getPos(),values));
        sent=key;for(int i=0;i<3;i++)seat.setModeChannels(i,values[i]);
    }
    public void updateScreen(){super.updateScreen();for(GuiTextField f:fields)f.updateCursorCounter();String current=textKey();if(!current.equals(observed)){observed=current;delay=6;}if(delay>0)delay--;else apply();if(closing){apply();if(busy==null || !busy.getAsBoolean()){if(close!=null)close.run();else mc.player.closeScreen();}}}
    protected void actionPerformed(GuiButton b){closing=true;apply();if(busy==null || !busy.getAsBoolean()){if(close!=null)close.run();else mc.player.closeScreen();}}
    protected void keyTyped(char c,int key)throws IOException{if(key==1 || key==mc.gameSettings.keyBindInventory.getKeyCode()){actionPerformed(new GuiButton(4,0,0,"Close"));return;}boolean used=false;for(GuiTextField f:fields)used|=f.textboxKeyTyped(c,key);if(!used)super.keyTyped(c,key);}
    protected void mouseClicked(int x,int y,int b)throws IOException{super.mouseClicked(x,y,b);for(GuiTextField f:fields)f.mouseClicked(x,y,b);}
    public void onGuiClosed(){if(fields[0]!=null)apply();super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF17212B);DialogHeader.logo(guiLeft,guiTop,xSize);}
    protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Vehicle Controller: mode channels",14,12,0xFFFFFF);String[] names={"Block / Park","Taxi","Hover"};for(int i=0;i<3;i++)fontRenderer.drawString(names[i],14,45+i*29,0xDAE8F0);fontRenderer.drawString("0 = unassigned; rising power selects mode",14,132,0xAABBCC);fontRenderer.drawString("Valid changes apply automatically",14,144,0xAABBCC);}
    public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();for(GuiTextField f:fields)f.drawTextBox();}
}
