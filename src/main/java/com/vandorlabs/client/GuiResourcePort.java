package com.vandorlabs.client;

import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.ports.*;
import com.vandorlabs.redstone.ChannelList;
import java.io.IOException;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Keyboard;

/** The standard compact General/Redstone editor; storage contents are never editable. */
public final class GuiResourcePort extends GuiMaterialPicker {
    private final ResourcePortContainer container;
    private ResourcePortSettings settings;
    private int mode,page,quietTicks;
    private int previousGuiScale=-1;
    private boolean dirty,ready,batteryBlock;
    private boolean battery(){return container.tile instanceof com.vandorlabs.tiles.TileEntityBattery;}
    private GuiTextField throughput,channels;
    private HousingTextureList textures;
    @Override protected HousingTextureList materialPicker(){return page==2?textures:null;}
    java.util.List<GuiButton> controls(){return buttonList;}
    int left(){return guiLeft;}
    int top(){return guiTop;}
    boolean controlsFit(){if(guiLeft<0||guiTop<0||guiLeft+xSize>width||guiTop+ySize>height)return false;for(GuiButton button:buttonList)if(button.x<0||button.y<0||button.x+button.width>width||button.y+button.height>height)return false;return true;}
    public GuiResourcePort(ResourcePortContainer container){super(container);this.container=container;settings=container.tile.settings();mode=container.tile.redstoneMode();batteryBlock=!battery()||((com.vandorlabs.tiles.TileEntityBattery)container.tile).isBlockShape();xSize=com.vandorlabs.ui.ResourcePortDialog.WIDTH;ySize=com.vandorlabs.ui.ResourcePortDialog.HEIGHT;}
    @Override public void initGui(){
        String rateText=throughput==null?settings.throughputText():throughput.getText(),channelText=channels==null?ChannelFields.text(container.tile.getRedstoneChannels()):channels.getText();
        xSize=com.vandorlabs.ui.ResourcePortDialog.WIDTH;ySize=com.vandorlabs.ui.ResourcePortDialog.HEIGHT;if(width<xSize+12||height<ySize+12){if(previousGuiScale<0)previousGuiScale=mc.gameSettings.guiScale;int scale=new ScaledResolution(mc).getScaleFactor();ScaledResolution resolution;do{mc.gameSettings.guiScale=Math.max(1,--scale);resolution=new ScaledResolution(mc);}while((resolution.getScaledWidth()<xSize+12||resolution.getScaledHeight()<ySize+12)&&scale>1);width=resolution.getScaledWidth();height=resolution.getScaledHeight();}
        super.initGui();ready=false;buttonList.clear();Keyboard.enableRepeatEvents(true);
        for(com.vandorlabs.ui.ResourcePortDialog.Control control:com.vandorlabs.ui.ResourcePortDialog.controls(guiLeft,guiTop,page,settings,mode,battery(),batteryBlock)){
            GuiButton button=control.tab?new DialogTabButton(control.id,control.x,control.y,control.width,control.height,control.label):new GuiButton(control.id,control.x,control.y,control.width,control.height,control.label);button.enabled=control.enabled;buttonList.add(button);
        }
        throughput=new GuiTextField(1,fontRenderer,guiLeft+12,guiTop+com.vandorlabs.ui.ResourcePortDialog.RATE_Y,com.vandorlabs.ui.ResourcePortDialog.FIELD_WIDTH,com.vandorlabs.ui.ResourcePortDialog.FIELD_HEIGHT);throughput.setMaxStringLength(19);throughput.setText(rateText);throughput.setVisible(page==0);
        channels=new GuiTextField(2,fontRenderer,guiLeft+12,guiTop+com.vandorlabs.ui.ResourcePortDialog.CHANNEL_Y,com.vandorlabs.ui.ResourcePortDialog.FIELD_WIDTH,com.vandorlabs.ui.ResourcePortDialog.FIELD_HEIGHT);ChannelFields.configure(channels);channels.setText(channelText);channels.setVisible(page==1);
        if(page==2)textures=HousingTextureList.forHousing(guiLeft+12,guiTop+62,com.vandorlabs.ui.ResourcePortDialog.housingWidth(battery()),settings.housingChoice,com.vandorlabs.ui.ResourcePortDialog.housingRows(),battery()?"vandorlabs:blocks/imported/power/battery_cell_bank":"vandorlabs:blocks/resource_ports/port_hull").keepView(textures).custom(this::chooseHousing);
        labels();ready=true;
    }
    private void chooseHousing(int choice){settings=settings.withHousing(choice);dirty=true;save();}
    private String title(){return battery()?"Battery Block":container.tile instanceof PowerPortTile?"Power Port":container.tile instanceof FluidPortTile?"Fluid Port":"Matter Port";}
    private String unit(){return container.tile instanceof PowerPortTile?"RF":container.tile instanceof FluidPortTile?"mB":"items";}
    private void labels(){for(com.vandorlabs.ui.ResourcePortDialog.Control control:com.vandorlabs.ui.ResourcePortDialog.controls(guiLeft,guiTop,page,settings,mode,battery(),batteryBlock))for(GuiButton button:buttonList)if(button.id==control.id)button.displayString=control.label;}
    private boolean save(){
        Long rate=ResourcePortSettings.parseThroughput(throughput.getText());ChannelList parsed=ChannelFields.parse(channels);throughput.setTextColor(rate==null?0xff7777:0xe0e0e0);channels.setTextColor(parsed==null?0xff7777:0xe0e0e0);if(rate==null||parsed==null)return false;
        settings=settings.withTransfer(rate,settings.pullInputs,settings.onTexture);PacketHandler.INSTANCE.sendToServer(new ResourcePortMessage(inventorySlots.windowId,settings,mode,parsed));dirty=false;return true;
    }
    @Override protected void actionPerformed(GuiButton button){
        if(button.id==100||button.id==101||button.id==102){page=button.id-100;initGui();return;}
        if(button.id==40&&battery()){batteryBlock=!batteryBlock;PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageTankShape(inventorySlots.windowId,batteryBlock));button.displayString="Shape: "+(batteryBlock?"Block":"Hexagon");return;}
        if(button.id==0){if(save())mc.player.closeScreen();return;}
        if(button.id>=10&&button.id<16){int side=button.id-10;settings=settings.withFace(side,ResourcePortSettings.Face.values()[GuiOptionCycle.next(settings.face(side).ordinal(),3)]);}
        else if(button.id==20)settings=settings.withTransfer(settings.throughput,!settings.pullInputs,settings.onTexture);
        else if(button.id==21)settings=settings.withTransfer(settings.throughput,settings.pullInputs,ResourcePortSettings.OnTexture.values()[GuiOptionCycle.next(settings.onTexture.ordinal(),3)]);
        else if(button.id==30)mode=GuiOptionCycle.next(mode,3);dirty=true;save();labels();
    }
    @Override public void updateScreen(){super.updateScreen();throughput.updateCursorCounter();channels.updateCursorCounter();if(dirty&&++quietTicks>=4)save();}
    @Override protected void keyTyped(char ch,int key)throws IOException{if(page==2&&textures!=null&&textures.searchKey(ch,key))return;if(page!=2&&(page==0?throughput:channels).textboxKeyTyped(ch,key)){dirty=true;quietTicks=0;return;}if(key==Keyboard.KEY_ESCAPE&&dirty)save();super.keyTyped(ch,key);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,10,11,12,13,14,15,20,21,30,40))return;if(page==2&&textures!=null){int before=textures.selected();if(textures.click(x,y,button)){if(before!=textures.selected())chooseHousing(textures.selected());return;}}else (page==0?throughput:channels).mouseClicked(x,y,button);super.mouseClicked(x,y,button);}
    @Override public void onGuiClosed(){if(ready&&dirty)save();Keyboard.enableRepeatEvents(false);super.onGuiClosed();if(previousGuiScale>=0){mc.gameSettings.guiScale=previousGuiScale;previousGuiScale=-1;}}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xff304858);DialogHeader.logo(guiLeft,guiTop,xSize,28);if(page==2&&textures!=null)textures.draw(fontRenderer,x,y);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(title(),12,10,0xffffff);if(page==0){fontRenderer.drawString(com.vandorlabs.ui.ResourcePortDialog.RATE_LABEL,12,145,0xd5e0e8);}else if(page==2&&battery()){int x0=xSize/2+8,y0=68;int amount=container.batteryValues[0];fontRenderer.drawString("Charge",x0,y0,0xd5e0e8);fontRenderer.drawString(amount+" / 100000 RF",x0,y0+18,0xc9d9e2);drawRect(x0,y0+42,xSize-12,y0+57,0xff14241a);drawRect(x0+1,y0+43,x0+1+(int)((xSize-21-x0)*Math.max(0,Math.min(1,amount/100000D))),y0+56,0xff62ed82);}else if(page==1){fontRenderer.drawString(com.vandorlabs.ui.ResourcePortDialog.CHANNEL_LABEL,12,70,0xd5e0e8);fontRenderer.drawSplitString(com.vandorlabs.ui.ResourcePortDialog.CHANNEL_HINT,12,150,xSize-24,0xc9d9e2);}}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();throughput.drawTextBox();channels.drawTextBox();if(page==0&&throughput.getText().isEmpty()&&!throughput.isFocused())fontRenderer.drawString("Unlimited",guiLeft+16,guiTop+163,0x8294a4);if(page==0)fontRenderer.drawString(unit()+"/tick",guiLeft+xSize-16-fontRenderer.getStringWidth(unit()+"/tick"),guiTop+163,0x8294a4);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
