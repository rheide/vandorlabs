package com.vandorlabs.client;

import com.vandorlabs.container.ContainerWirelessRedstone;
import com.vandorlabs.tiles.TileEntityWirelessRedstone;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fml.client.config.GuiSlider;
import java.io.IOException;

/** Compact channel editor with the same level sliders and reverse cycles as other dialogs. */
public final class GuiWirelessRedstone extends GuiContainer {
    private final TileEntityWirelessRedstone tile;
    private GuiTextField channels;
    private boolean analog,ready,dirty;
    private int on,off,face;
    public GuiWirelessRedstone(ContainerWirelessRedstone container){super(container);tile=container.tile;analog=tile.analog();on=tile.onLevel();off=tile.offLevel();face=tile.port().getIndex();xSize=280;ySize=tile.transmitter()?160:224;}
    @Override public void initGui(){String text=channels==null?tile.getRedstoneChannels().toString():channels.getText();xSize=Math.min(280,width-12);super.initGui();ready=false;buttonList.clear();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        channels=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+48,xSize-24,18);ChannelFields.configure(channels);channels.setText(text);if(tile.transmitter()){channels.setVisible(false);buttonList.add(new GuiButton(90,guiLeft+12,guiTop+46,xSize-24,20,"Redstone Transmitter..."));}
        buttonList.add(new GuiButton(1,guiLeft+12,guiTop+76,xSize-24,20,"Signal: "+(analog?"Analog":"Digital")));
        buttonList.add(new GuiButton(2,guiLeft+12,guiTop+102,xSize-24,20,(tile.transmitter()?"Input":"Output")+" port: "+net.minecraft.util.EnumFacing.getFront(face).getName()));
        if(!tile.transmitter()){
            GuiSlider onSlider=new GuiSlider(3,guiLeft+12,guiTop+128,xSize-24,20,"On level: ","",0,15,on,false,true,slider->{on=slider.getValueInt();if(ready)dirty=true;});onSlider.enabled=!analog;buttonList.add(onSlider);
            buttonList.add(new GuiSlider(4,guiLeft+12,guiTop+154,xSize-24,20,"Off level: ","",0,15,off,false,true,slider->{off=slider.getValueInt();if(ready)dirty=true;}));
        }
        buttonList.add(new GuiButton(5,guiLeft+12,guiTop+ySize-30,xSize-24,20,"Done"));ready=true;
    }
    private void save(){com.vandorlabs.redstone.ChannelList parsed=ChannelFields.parse(channels);channels.setTextColor(parsed==null?0xff7777:0xe0e0e0);if(parsed==null)return;com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageWirelessRedstone(inventorySlots.windowId,parsed.toString(),analog,on,off,face));dirty=false;}
    @Override public void updateScreen(){channels.updateCursorCounter();if(dirty)save();}
    @Override protected void actionPerformed(GuiButton button){if(button instanceof GuiSlider)return;if(button.id==90){save();com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(com.vandorlabs.network.MessageTransmitterSlot.open(tile.getPos(),0));return;}if(button.id==5){save();mc.player.closeScreen();return;}if(button.id==1)analog=!analog;else if(button.id==2)face=GuiOptionCycle.next(face,6);save();initGui();}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,2))return;channels.mouseClicked(x,y,button);super.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char ch,int key)throws IOException{if(channels.textboxKeyTyped(ch,key))save();else super.keyTyped(ch,key);}
    @Override public void onGuiClosed(){if(ready)save();org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xff304858);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(tile.transmitter()?"Redstone Transmitter Block":"Redstone Receiver",12,10,0xffffff);if(!tile.transmitter())fontRenderer.drawString("Channels (0 = none)",12,34,0xffffff);if(!tile.transmitter()&&analog)fontRenderer.drawString("On level follows received signal",12,180,0xc9d9e2);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();channels.drawTextBox();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
