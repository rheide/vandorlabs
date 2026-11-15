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
    private boolean dirty,ready;
    private GuiTextField throughput,channels;
    private HousingTextureList textures;
    @Override protected HousingTextureList materialPicker(){return page==2?textures:null;}
    java.util.List<GuiButton> controls(){return buttonList;}
    int left(){return guiLeft;}
    int top(){return guiTop;}
    boolean controlsFit(){if(guiLeft<0||guiTop<0||guiLeft+xSize>width||guiTop+ySize>height)return false;for(GuiButton button:buttonList)if(button.x<0||button.y<0||button.x+button.width>width||button.y+button.height>height)return false;return true;}
    public GuiResourcePort(ResourcePortContainer container){super(container);this.container=container;settings=container.tile.settings();mode=container.tile.redstoneMode();xSize=308;ySize=280;}
    @Override public void initGui(){
        String rateText=throughput==null?settings.throughputText():throughput.getText(),channelText=channels==null?ChannelFields.text(container.tile.getRedstoneChannels()):channels.getText();
        xSize=308;ySize=280;if(width<xSize+12||height<ySize+12){if(previousGuiScale<0)previousGuiScale=mc.gameSettings.guiScale;int scale=new ScaledResolution(mc).getScaleFactor();ScaledResolution resolution;do{mc.gameSettings.guiScale=Math.max(1,--scale);resolution=new ScaledResolution(mc);}while((resolution.getScaledWidth()<xSize+12||resolution.getScaledHeight()<ySize+12)&&scale>1);width=resolution.getScaledWidth();height=resolution.getScaledHeight();}
        super.initGui();ready=false;buttonList.clear();Keyboard.enableRepeatEvents(true);
        int half=(xSize-28)/2,tab=(xSize-32)/3;
        buttonList.add(new DialogTabButton(100,guiLeft+12,guiTop+32,tab,20,"General"));buttonList.add(new DialogTabButton(102,guiLeft+16+tab,guiTop+32,tab,20,"Housing"));buttonList.add(new DialogTabButton(101,guiLeft+20+tab*2,guiTop+32,tab,20,"Redstone"));
        for(GuiButton button:buttonList)if(button.id==100+page)button.enabled=false;
        throughput=new GuiTextField(1,fontRenderer,guiLeft+12,guiTop+158,xSize-24,18);throughput.setMaxStringLength(19);throughput.setText(rateText);throughput.setVisible(page==0);
        channels=new GuiTextField(2,fontRenderer,guiLeft+12,guiTop+84,xSize-24,18);ChannelFields.configure(channels);channels.setText(channelText);channels.setVisible(page==1);
        if(page==0){for(int side=0;side<6;side++)buttonList.add(new GuiButton(10+side,guiLeft+12+(side%2)*(half+4),guiTop+62+(side/2)*26,half,20,""));buttonList.add(new GuiButton(20,guiLeft+12,guiTop+186,xSize-24,20,""));buttonList.add(new GuiButton(21,guiLeft+12,guiTop+212,xSize-24,20,""));}
        else if(page==1)buttonList.add(new GuiButton(30,guiLeft+12,guiTop+116,xSize-24,20,""));
        if(page==2)textures=HousingTextureList.forHousing(guiLeft+12,guiTop+62,xSize-31,settings.housingChoice,(ySize-100)/HousingTextureList.ROW_HEIGHT,"vandorlabs:blocks/resource_ports/port_hull").keepView(textures).custom(this::chooseHousing);
        buttonList.add(new GuiButton(0,guiLeft+12,guiTop+ySize-30,xSize-24,20,"Done"));labels();ready=true;
    }
    private void chooseHousing(int choice){settings=settings.withHousing(choice);dirty=true;save();}
    private String title(){return container.tile instanceof PowerPortTile?"Power Port":container.tile instanceof FluidPortTile?"Fluid Port":"Matter Port";}
    private String unit(){return container.tile instanceof PowerPortTile?"RF":container.tile instanceof FluidPortTile?"mB":"items";}
    private void labels(){for(GuiButton button:buttonList){if(button.id>=10&&button.id<16){int side=button.id-10;String name=EnumFacing.getFront(side).getName();button.displayString=Character.toUpperCase(name.charAt(0))+name.substring(1)+": "+new String[]{"Disabled","Input","Output"}[settings.face(side).ordinal()];}else if(button.id==20)button.displayString="Pull from inputs: "+(settings.pullInputs?"On":"Off");else if(button.id==21)button.displayString="On texture: "+new String[]{"Redstone active","Connected","Transferring"}[settings.onTexture.ordinal()];else if(button.id==30)button.displayString="Input: "+new String[]{"Always active","ON activates","OFF activates"}[mode];}}
    private boolean save(){
        Long rate=ResourcePortSettings.parseThroughput(throughput.getText());ChannelList parsed=ChannelFields.parse(channels);throughput.setTextColor(rate==null?0xff7777:0xe0e0e0);channels.setTextColor(parsed==null?0xff7777:0xe0e0e0);if(rate==null||parsed==null)return false;
        settings=settings.withTransfer(rate,settings.pullInputs,settings.onTexture);PacketHandler.INSTANCE.sendToServer(new ResourcePortMessage(inventorySlots.windowId,settings,mode,parsed));dirty=false;return true;
    }
    @Override protected void actionPerformed(GuiButton button){
        if(button.id==100||button.id==101||button.id==102){page=button.id-100;initGui();return;}
        if(button.id==0){if(save())mc.player.closeScreen();return;}
        if(button.id>=10&&button.id<16){int side=button.id-10;settings=settings.withFace(side,ResourcePortSettings.Face.values()[GuiOptionCycle.next(settings.face(side).ordinal(),3)]);}
        else if(button.id==20)settings=settings.withTransfer(settings.throughput,!settings.pullInputs,settings.onTexture);
        else if(button.id==21)settings=settings.withTransfer(settings.throughput,settings.pullInputs,ResourcePortSettings.OnTexture.values()[GuiOptionCycle.next(settings.onTexture.ordinal(),3)]);
        else if(button.id==30)mode=GuiOptionCycle.next(mode,3);dirty=true;save();labels();
    }
    @Override public void updateScreen(){super.updateScreen();throughput.updateCursorCounter();channels.updateCursorCounter();if(dirty&&++quietTicks>=4)save();}
    @Override protected void keyTyped(char ch,int key)throws IOException{if(page==2&&textures!=null&&textures.searchKey(ch,key))return;if(page!=2&&(page==0?throughput:channels).textboxKeyTyped(ch,key)){dirty=true;quietTicks=0;return;}if(key==Keyboard.KEY_ESCAPE&&dirty)save();super.keyTyped(ch,key);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,10,11,12,13,14,15,20,21,30))return;if(page==2&&textures!=null){int before=textures.selected();if(textures.click(x,y,button)){if(before!=textures.selected())chooseHousing(textures.selected());return;}}else (page==0?throughput:channels).mouseClicked(x,y,button);super.mouseClicked(x,y,button);}
    @Override public void onGuiClosed(){if(ready&&dirty)save();Keyboard.enableRepeatEvents(false);super.onGuiClosed();if(previousGuiScale>=0){mc.gameSettings.guiScale=previousGuiScale;previousGuiScale=-1;}}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xff304858);DialogHeader.logo(guiLeft,guiTop,xSize,28);if(page==2&&textures!=null)textures.draw(fontRenderer,x,y);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(title(),12,10,0xffffff);if(page==0){fontRenderer.drawString("Shared output limit (blank for unlimited)",12,145,0xd5e0e8);}else if(page==1){fontRenderer.drawString("Receive channels",12,70,0xd5e0e8);fontRenderer.drawSplitString("Redstone gates incoming and outgoing transfers. Empty channels use adjacent redstone.",12,150,xSize-24,0xc9d9e2);}}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();throughput.drawTextBox();channels.drawTextBox();if(page==0&&throughput.getText().isEmpty()&&!throughput.isFocused())fontRenderer.drawString("Unlimited",guiLeft+16,guiTop+163,0x8294a4);if(page==0)fontRenderer.drawString(unit()+"/tick",guiLeft+xSize-16-fontRenderer.getStringWidth(unit()+"/tick"),guiTop+163,0x8294a4);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
