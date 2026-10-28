package com.vandorlabs.client;

import com.vandorlabs.redstone.ChannelList;

import com.vandorlabs.tiles.TileEntityLandingGear;
import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fml.client.config.GuiSlider;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

public final class GuiLandingGear extends GuiContainer {
    private final TileEntityLandingGear tile;
    private int mode,size,page;
    private GuiTextField channel;
    GuiSlider length;
    private int lastSentPixels,seenRevision;
    public GuiLandingGear(TileEntityLandingGear tile){super(new ContainerRedstoneChannel(tile));this.tile=tile;mode=tile.getMode();size=tile.getSize();lastSentPixels=tile.getExtensionPixels();seenRevision=tile.getConfigurationRevision();xSize=280;ySize=246;}
    public void initGui(){
        String draft=channel==null?tile.getRedstoneChannels().toString():channel.getText();super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        buttonList.add(new RedstoneActivationButton(0,guiLeft+14,guiTop+66,252,mode));
        buttonList.add(new GuiButton(4,guiLeft+14,guiTop+66,252,20,sizeLabel()));
        channel=new GuiTextField(1,fontRenderer,guiLeft+156,guiTop+102,110,18);
        ChannelFields.configure(channel);channel.setText(draft);channel.setVisible(page==1);channel.setFocused(false);
        length=new GuiSlider(2,guiLeft+14,guiTop+98,252,20,"","",0,8,lastSentPixels/8D,false,true,slider->{
            int pixels=slider.getValueInt()*8;
            slider.setValue(pixels/8D);slider.displayString=lengthLabel(pixels);
            if(pixels!=lastSentPixels){lastSentPixels=pixels;PacketHandler.INSTANCE.sendToServer(new MessageLandingGear(tile.getPos(),pixels));}
        });
        length.displayString=lengthLabel(lastSentPixels);
        buttonList.add(length);for(GuiButton b:buttonList)b.visible=b.id==0?page==1:page==0;buttonList.add(new DialogTabButton(100,guiLeft+14,guiTop+32,122,20,"General"));buttonList.add(new DialogTabButton(101,guiLeft+144,guiTop+32,122,20,"Redstone"));for(GuiButton b:buttonList)if(b.id>=100)b.enabled=b.id!=100+page;if(page==1)buttonList.add(new GuiButton(90,guiLeft+14,guiTop+134,252,20,"Redstone Transmitter..."));buttonList.add(new GuiButton(3,guiLeft+14,guiTop+ySize-30,252,20,"Done"));
    }
    private String sizeLabel(){if(tile.getBlockType() instanceof com.vandorlabs.blocks.BlockNonWheeledLandingGear)return size==4?"Size: Large (2x2)":"Size: Small (1 block)";String name=(size==4?"extra large (2×2)":com.vandorlabs.blocks.BlockTelescopicLandingGear.SIZES[size]).replace('_',' ');return "Size: "+Character.toUpperCase(name.charAt(0))+name.substring(1);}
    private static String lengthLabel(int pixels){return "Extended length: "+(pixels/16D)+" blocks";}
    private void submit(){
        ChannelList value=ChannelFields.parse(channel);if(value==null)return;
        PacketHandler.INSTANCE.sendToServer(new MessageLandingGear(tile.getPos(),mode,value.first(),length.getValueInt()*8,size).withChannels(value));mc.player.closeScreen();
    }
    protected void actionPerformed(GuiButton b){if(b.id==100||b.id==101){page=b.id-100;initGui();return;}if(b.id==90){ChannelList channels=ChannelFields.parse(channel);if(channels!=null)PacketHandler.INSTANCE.sendToServer(new MessageLandingGear(tile.getPos(),mode,channels.first(),length.getValueInt()*8,size).withChannels(channels));PacketHandler.INSTANCE.sendToServer(MessageTransmitterSlot.open(tile.getPos(),0));return;}if(b.id==0){mode=GuiOptionCycle.next(mode,3);for(GuiButton button:buttonList)if(button.id==0)((RedstoneActivationButton)button).setMode(mode);}else if(b.id==4){size=tile.getBlockType() instanceof com.vandorlabs.blocks.BlockNonWheeledLandingGear?(size==0?4:0):GuiOptionCycle.next(size,com.vandorlabs.blocks.BlockTelescopicLandingGear.SIZES.length);for(GuiButton button:buttonList)if(button.id==4)button.displayString=sizeLabel();
            PacketHandler.INSTANCE.sendToServer(new MessageLandingGear(tile.getPos(),-1,0,length.getValueInt()*8,size));
        }else if(b.id==3)submit();}
    protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_RETURN){submit();return;}if(page!=1||!channel.textboxKeyTyped(c,key))super.keyTyped(c,key);}
    protected void mouseClicked(int x,int y,int b)throws IOException{
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,b,this::actionPerformed,0,4))return;super.mouseClicked(x,y,b);if(page==1)channel.mouseClicked(x,y,b);}
    public void updateScreen(){
        super.updateScreen();channel.updateCursorCounter();
        if(!length.dragging&&seenRevision!=tile.getConfigurationRevision()){
            seenRevision=tile.getConfigurationRevision();lastSentPixels=tile.getExtensionPixels();size=tile.getSize();
            for(GuiButton button:buttonList)if(button.id==4)button.displayString=sizeLabel();
            length.setValue(lastSentPixels/8D);length.displayString=lengthLabel(lastSentPixels);
        }
    }
    public void onGuiClosed(){super.onGuiClosed();Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xEE171B23);DialogHeader.logo(guiLeft,guiTop,xSize);}
    protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString(fontRenderer.trimStringToWidth(new net.minecraft.item.ItemStack(tile.getBlockType()).getDisplayName(),xSize-50),14,12,0xFFFFFF);
        if(page==1)fontRenderer.drawString("Receive channels",14,108,0xDDDDDD);
        if(page==1)fontRenderer.drawString("On: extend with power; Off: without",14,168,0xBBBBBB);
        if(page==1)fontRenderer.drawString("Disabled: right-click to move",14,182,0xBBBBBB);
    }
    public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();channel.drawTextBox();}
}
