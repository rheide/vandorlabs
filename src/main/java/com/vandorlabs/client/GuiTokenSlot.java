package com.vandorlabs.client;
import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import java.io.IOException;

public final class GuiTokenSlot extends GuiContainer {
    private final TileEntityTokenSlot tile;
    private int behavior,allowed;
    private GuiTextField channels;private int page;
    public GuiTokenSlot(ContainerTokenSlot container){super(container);tile=container.tile;behavior=tile.behavior;allowed=tile.effectiveAllowed();xSize=308;ySize=160;}
    @Override public void initGui(){
        String text=channels==null?tile.getRedstoneChannels().toString():channels.getText();xSize=Math.min(308,width-12);ySize=206;super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);buttonList.clear();
        buttonList.add(new DialogTabButton(100,guiLeft+12,guiTop+34,138,20,"Redstone"));buttonList.add(new DialogTabButton(101,guiLeft+154,guiTop+34,142,20,"Security"));for(GuiButton b:buttonList)b.enabled=b.id!=100+page;
        channels=new GuiTextField(2,fontRenderer,guiLeft+12,guiTop+76,xSize-24,18);ChannelFields.configure(channels);channels.setText(text);channels.setVisible(false);channels.setFocused(false);
        if(page==0){buttonList.add(new GuiButton(1,guiLeft+12,guiTop+106,xSize-24,20,TileEntityTokenSlot.behaviorName(behavior)));buttonList.add(new GuiButton(90,guiLeft+12,guiTop+74,xSize-24,20,"Redstone Transmitter..."));}
        buttonList.add(new GuiButton(3,guiLeft+12,guiTop+ySize-30,xSize-24,20,"Done"));
    }

    int behaviorDraft(){return behavior;}
    int acceptedCompany(){return allowed;}
    String companyLabel(){return CredentialFilterPicker.label(tile.chip(),allowed);}
    private void labels(){for(GuiButton button:buttonList)if(button.id==1)button.displayString=TileEntityTokenSlot.behaviorName(behavior);}
    private void save(){if(com.vandorlabs.redstone.ChannelList.parse(channels.getText())==null){channels.setTextColor(0xff7777);return;}channels.setTextColor(0xffffff);com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageTokenSlot(inventorySlots.windowId,behavior,false,allowed,channels.getText()));}
    @Override protected void actionPerformed(GuiButton button){if(button.id==100||button.id==101){page=button.id-100;initGui();return;}if(button.id==90){save();com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(com.vandorlabs.network.MessageTransmitterSlot.open(tile.getPos(),0));return;}if(button.id==3){mc.player.closeScreen();return;}behavior=GuiOptionCycle.next(behavior,4);save();labels();}
    @Override protected void keyTyped(char ch,int key)throws IOException{super.keyTyped(ch,key);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1))return;
        if(page==1&&CredentialFilterPicker.click(this,x,y,button,guiLeft+12,guiTop+82,tile.chip(),allowed,mask->{allowed=mask;save();}))return;
        super.mouseClicked(x,y,button);
    }
    @Override public void updateScreen(){channels.updateCursorCounter();labels();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mx,int my){
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xff304858);DialogHeader.logo(guiLeft,guiTop,xSize,28);
        if(page==1)CredentialFilterPicker.draw(mc,fontRenderer,guiLeft+12,guiTop+82,tile.chip(),allowed);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(tile.getDisplayName().getUnformattedText(),12,10,0xffffff);if(page==1)fontRenderer.drawString("Accepted credential",12,68,0xdae8f0);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);GlStateManager.disableLighting();GlStateManager.disableDepth();channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
