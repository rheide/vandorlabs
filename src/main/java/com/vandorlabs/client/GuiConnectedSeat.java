package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityConnectedSeat;
import com.vandorlabs.container.ContainerProgrammableChair;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;

public final class GuiConnectedSeat extends GuiContainer {
    private final TileEntityConnectedSeat tile;
    private static final String[] HEIGHTS={"Low (-2 px)","Default","High (+2 px)"};
    public GuiConnectedSeat(TileEntityConnectedSeat tile){super(new ContainerProgrammableChair(tile));this.tile=tile;xSize=260;ySize=134;}
    public void initGui(){super.initGui();buttonList.clear();
        buttonList.add(new GuiButton(0,guiLeft+14,guiTop+30,232,20,"Join: "+(tile.isJoin()?"On":"Off")));
        buttonList.add(new GuiButton(1,guiLeft+14,guiTop+57,232,20,"Height: "+HEIGHTS[tile.getHeight()]));
        buttonList.add(new GuiButton(2,guiLeft+14,guiTop+100,232,20,"Done"));
    }
    protected void actionPerformed(GuiButton button){
        if(button.id==2){mc.displayGuiScreen(null);return;}
        if(button.id==0)tile.setJoin(!tile.isJoin());else tile.setHeight((tile.getHeight()+1)%3);
        PacketHandler.INSTANCE.sendToServer(new MessageConnectedSeat(tile.getPos(),tile.isJoin(),tile.getHeight()));initGui();
    }
    protected void drawGuiContainerBackgroundLayer(float ticks,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xEE171B23);}
    protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString(new net.minecraft.item.ItemStack(tile.getBlockType()).getDisplayName(),14,12,0xFFFFFF);
        fontRenderer.drawString("Default legs: +1 px",14,84,0xBBBBBB);
    }
}
