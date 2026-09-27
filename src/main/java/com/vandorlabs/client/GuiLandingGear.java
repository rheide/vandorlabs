package com.vandorlabs.client;

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
    private int mode;
    private GuiTextField channel;
    private GuiSlider length;
    private static final String[] MODES={"Disabled","On","Off"};
    public GuiLandingGear(TileEntityLandingGear tile){super(new ContainerRedstoneChannel(tile));this.tile=tile;mode=tile.getMode();xSize=280;ySize=188;}
    public void initGui(){
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        buttonList.add(new GuiButton(0,guiLeft+14,guiTop+32,252,20,"Redstone: "+MODES[mode]));
        channel=new GuiTextField(1,fontRenderer,guiLeft+156,guiTop+61,110,18);
        channel.setMaxStringLength(10);channel.setValidator(s->s.isEmpty()||s.matches("[0-9]{1,10}"));channel.setText(Integer.toString(tile.getRedstoneChannel()));
        length=new GuiSlider(2,guiLeft+14,guiTop+88,252,20,"","",0,64,tile.getExtensionPixels(),false,true,
                slider->slider.displayString=String.format(java.util.Locale.ROOT,"Extended length: %.4f blocks",slider.getValueInt()/16D));
        length.displayString=String.format(java.util.Locale.ROOT,"Extended length: %.4f blocks",tile.getExtensionPixels()/16D);
        buttonList.add(length);buttonList.add(new GuiButton(3,guiLeft+14,guiTop+154,252,20,"Done"));
    }
    private void submit(){
        try{int value=Integer.parseInt(channel.getText());if(value<0)return;
            PacketHandler.INSTANCE.sendToServer(new MessageLandingGear(tile.getPos(),mode,value,length.getValueInt()));mc.player.closeScreen();
        }catch(NumberFormatException ignored){}
    }
    protected void actionPerformed(GuiButton b){if(b.id==0){mode=(mode+1)%3;b.displayString="Redstone: "+MODES[mode];}else if(b.id==3)submit();}
    protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_RETURN){submit();return;}if(!channel.textboxKeyTyped(c,key))super.keyTyped(c,key);}
    protected void mouseClicked(int x,int y,int b)throws IOException{super.mouseClicked(x,y,b);channel.mouseClicked(x,y,b);}
    public void updateScreen(){super.updateScreen();channel.updateCursorCounter();}
    public void onGuiClosed(){super.onGuiClosed();Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xEE171B23);}
    protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString(new net.minecraft.item.ItemStack(tile.getBlockType()).getDisplayName(),14,12,0xFFFFFF);
        fontRenderer.drawString("Channel (0 = none)",14,66,0xDDDDDD);
        fontRenderer.drawString("On: extend with power; Off: without",14,119,0xBBBBBB);
        fontRenderer.drawString("Disabled: right-click to move",14,133,0xBBBBBB);
    }
    public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();channel.drawTextBox();}
}
