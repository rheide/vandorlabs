package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModularGear;
import com.vandorlabs.gear.track.TrackTile;
import com.vandorlabs.network.MessageModularGear;
import com.vandorlabs.network.PacketHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import java.io.IOException;

/** Track appearance has no independent arm input or motion settings. */
public final class GuiTrack extends GuiContainer {
    private final ContainerModularGear container;
    private final TrackTile track;
    private int style;
    public GuiTrack(ContainerModularGear container){
        super(container);if(container.track==null)throw new IllegalArgumentException("Track dialog needs a Track");
        this.container=container;track=container.track;style=track.style().ordinal();xSize=280;ySize=146;
    }
    @Override public void initGui(){super.initGui();buttonList.clear();buttonList.add(new GuiButton(0,guiLeft+40,guiTop+40,200,20,""));buttonList.add(new GuiButton(4,guiLeft+40,guiTop+116,200,20,"Done"));labels();}
    private void labels(){buttonList.get(0).displayString="Style: "+new String[]{"Compact","Armoured","Bogie"}[style];buttonList.get(0).enabled=!container.tile.isController()||container.tile.fullyDeployed();}
    @Override protected void actionPerformed(GuiButton button){
        if(button.id==4){mc.player.closeScreen();return;}
        if(button.id!=0)return;style=GuiOptionCycle.next(style,3);NBTTagCompound settings=new NBTTagCompound();settings.setInteger("TrackStyle",style);PacketHandler.INSTANCE.sendToServer(new MessageModularGear(inventorySlots.windowId,settings));labels();
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString("Track",12,12,0xffffff);String failure=track.error();String status=failure.isEmpty()?track.status():"ERROR: "+failure;
        java.util.List<String> lines=fontRenderer.listFormattedStringToWidth(status,xSize-24);for(int i=0;i<Math.min(4,lines.size());i++)fontRenderer.drawString(lines.get(i),12,70+i*9,failure.isEmpty()?0xcdd7e4:0xff8888);
    }
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();super.drawScreen(x,y,partial);renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0))return;super.mouseClicked(x,y,button);}
    @Override public void updateScreen(){super.updateScreen();labels();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
