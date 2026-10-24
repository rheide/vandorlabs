package com.vandorlabs.client;

import com.vandorlabs.container.ContainerWirelessRedstone;
import com.vandorlabs.tiles.TileEntityWirelessRedstone;
import com.vandorlabs.ui.WirelessDialogLayout;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fml.client.config.GuiSlider;
import java.io.IOException;

/** Compact channel editor with the same level sliders and reverse cycles as other dialogs. */
public final class GuiWirelessRedstone extends GuiContainer {
    private final TileEntityWirelessRedstone tile;
    private GuiTextField channels;
    private WirelessDialogLayout layout;
    private boolean analog,ready,dirty;
    private int on,off,face;
    public GuiWirelessRedstone(ContainerWirelessRedstone container){super(container);tile=container.tile;analog=tile.analog();on=tile.onLevel();off=tile.offLevel();face=tile.port().getIndex();xSize=WirelessDialogLayout.MAX_WIDTH;ySize=tile.transmitter()?WirelessDialogLayout.TRANSMITTER_HEIGHT:WirelessDialogLayout.RECEIVER_HEIGHT;}
    @Override public void initGui(){
        String text=channels==null?tile.getRedstoneChannels().toString():channels.getText();layout=new WirelessDialogLayout(width,height,tile.transmitter());
        xSize=layout.width;ySize=layout.height;super.initGui();ready=false;buttonList.clear();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        com.vandorlabs.ui.ProgrammableDialogLayout.Button field=layout.channels();
        channels=new GuiTextField(field.id,fontRenderer,field.x,field.y,field.width,field.height);ChannelFields.configure(channels);channels.setValidator(WirelessDialogLayout::validChannelCharacters);channels.setText(text);
        for(WirelessDialogLayout.Control control:layout.controls(analog,on,off,face)){
            com.vandorlabs.ui.ProgrammableDialogLayout.Button bounds=control.bounds;GuiButton button;
            if(control.slider){
                boolean onSlider=bounds.id==WirelessDialogLayout.ON;
                button=new GuiSlider(bounds.id,bounds.x,bounds.y,bounds.width,bounds.height,WirelessDialogLayout.levelPrefix(onSlider),"",0,15,onSlider?on:off,false,true,slider->{if(onSlider)on=slider.getValueInt();else off=slider.getValueInt();if(ready)dirty=true;});
            }else button=new GuiButton(bounds.id,bounds.x,bounds.y,bounds.width,bounds.height,bounds.label);
            button.enabled=control.enabled;buttonList.add(button);
        }
        ready=true;
    }
    private void save(){com.vandorlabs.redstone.ChannelList parsed=ChannelFields.parse(channels);channels.setTextColor(parsed==null?0xff7777:0xe0e0e0);if(parsed==null)return;com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageWirelessRedstone(inventorySlots.windowId,parsed.toString(),analog,on,off,face));dirty=false;}
    @Override public void updateScreen(){channels.updateCursorCounter();if(dirty)save();}
    @Override protected void actionPerformed(GuiButton button){if(button instanceof GuiSlider)return;if(button.id==WirelessDialogLayout.DONE){save();mc.player.closeScreen();return;}if(button.id==WirelessDialogLayout.SIGNAL)analog=!analog;else if(button.id==WirelessDialogLayout.PORT)face=WirelessDialogLayout.nextFace(face,GuiOptionCycle.direction());save();initGui();}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,2))return;channels.mouseClicked(x,y,button);super.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char ch,int key)throws IOException{if(channels.textboxKeyTyped(ch,key))save();else super.keyTyped(ch,key);}
    @Override public void onGuiClosed(){if(ready)save();org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,WirelessDialogLayout.PANEL_COLOR);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+WirelessDialogLayout.HEADER_HEIGHT,WirelessDialogLayout.HEADER_COLOR);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(layout.title(),WirelessDialogLayout.LABEL_X,WirelessDialogLayout.TITLE_Y,0xffffff);fontRenderer.drawString(WirelessDialogLayout.CHANNEL_LABEL,WirelessDialogLayout.LABEL_X,WirelessDialogLayout.CHANNEL_LABEL_Y,0xffffff);if(!tile.transmitter()&&analog)fontRenderer.drawString(WirelessDialogLayout.ANALOG_HELP,WirelessDialogLayout.LABEL_X,WirelessDialogLayout.HELP_Y,0xc9d9e2);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();channels.drawTextBox();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
