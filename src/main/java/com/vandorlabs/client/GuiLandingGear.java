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
    private int mode,size;
    private GuiTextField channel;
    GuiSlider length;
    private int lastSentPixels,seenRevision;
    private static final String[] MODES={"Disabled","On","Off"};
    public GuiLandingGear(TileEntityLandingGear tile){super(new ContainerRedstoneChannel(tile));this.tile=tile;mode=tile.getMode();size=tile.getSize();lastSentPixels=tile.getExtensionPixels();seenRevision=tile.getConfigurationRevision();xSize=280;ySize=214;}
    public void initGui(){
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        buttonList.add(new GuiButton(0,guiLeft+14,guiTop+58,252,20,"Redstone: "+MODES[mode]));
        buttonList.add(new GuiButton(4,guiLeft+14,guiTop+32,252,20,sizeLabel()));
        channel=new GuiTextField(1,fontRenderer,guiLeft+156,guiTop+87,110,18);
        ChannelFields.configure(channel);channel.setText(tile.getRedstoneChannels().toString());
        length=new GuiSlider(2,guiLeft+14,guiTop+114,252,20,"","",0,8,tile.getExtensionPixels()/8D,false,true,slider->{
            int pixels=slider.getValueInt()*8;
            slider.setValue(pixels/8D);slider.displayString=lengthLabel(pixels);
            if(pixels!=lastSentPixels){lastSentPixels=pixels;PacketHandler.INSTANCE.sendToServer(new MessageLandingGear(tile.getPos(),pixels));}
        });
        length.displayString=lengthLabel(tile.getExtensionPixels());
        buttonList.add(length);buttonList.add(new GuiButton(3,guiLeft+14,guiTop+180,252,20,"Done"));
    }
    private String sizeLabel(){String name=(size==4?"extra large (2×2)":com.vandorlabs.blocks.BlockTelescopicLandingGear.SIZES[size]).replace('_',' ');return "Size: "+Character.toUpperCase(name.charAt(0))+name.substring(1);}
    private static String lengthLabel(int pixels){return "Extended length: "+(pixels/16D)+" blocks";}
    private void submit(){
        ChannelList value=ChannelFields.parse(channel);if(value==null)return;
        PacketHandler.INSTANCE.sendToServer(new MessageLandingGear(tile.getPos(),mode,value.first(),length.getValueInt()*8,size).withChannels(value));mc.player.closeScreen();
    }
    protected void actionPerformed(GuiButton b){if(b.id==0){mode=(mode+1)%3;buttonList.get(0).displayString="Redstone: "+MODES[mode];}else if(b.id==4){size=(size+1)%com.vandorlabs.blocks.BlockTelescopicLandingGear.SIZES.length;for(GuiButton button:buttonList)if(button.id==4)button.displayString=sizeLabel();
            PacketHandler.INSTANCE.sendToServer(new MessageLandingGear(tile.getPos(),-1,0,length.getValueInt()*8,size));
        }else if(b.id==3)submit();}
    protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_RETURN){submit();return;}if(!channel.textboxKeyTyped(c,key))super.keyTyped(c,key);}
    protected void mouseClicked(int x,int y,int b)throws IOException{super.mouseClicked(x,y,b);channel.mouseClicked(x,y,b);}
    public void updateScreen(){
        super.updateScreen();channel.updateCursorCounter();
        if(!length.dragging&&seenRevision!=tile.getConfigurationRevision()){
            seenRevision=tile.getConfigurationRevision();lastSentPixels=tile.getExtensionPixels();size=tile.getSize();
            for(GuiButton button:buttonList)if(button.id==4)button.displayString=sizeLabel();
            length.setValue(lastSentPixels/8D);length.displayString=lengthLabel(lastSentPixels);
        }
    }
    public void onGuiClosed(){super.onGuiClosed();Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xEE171B23);}
    protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString(new net.minecraft.item.ItemStack(tile.getBlockType()).getDisplayName(),14,12,0xFFFFFF);
        fontRenderer.drawString("Channels (0 = none)",14,92,0xDDDDDD);
        fontRenderer.drawString("On: extend with power; Off: without",14,145,0xBBBBBB);
        fontRenderer.drawString("Disabled: right-click to move",14,159,0xBBBBBB);
    }
    public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();channel.drawTextBox();}
}
