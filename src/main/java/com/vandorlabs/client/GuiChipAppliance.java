package com.vandorlabs.client;
import com.vandorlabs.container.ContainerChipAppliance;
import com.vandorlabs.ui.InfuserDialog;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
/** Reuses the standard machine shell, slot frames, header and player inventory layout. */
public final class GuiChipAppliance extends GuiContainer {
    private final ContainerChipAppliance container;
    public GuiChipAppliance(ContainerChipAppliance container){super(container);this.container=container;xSize=InfuserDialog.WIDTH;ySize=InfuserDialog.HEIGHT;}
    @Override public void initGui(){super.initGui();buttonList.add(new GuiButton(0,guiLeft+12,guiTop+182,72,20,container.tile.scanner()?"Scan":"Print"));}
    @Override protected void actionPerformed(GuiButton button){if(button.id==0)mc.playerController.sendEnchantPacket(inventorySlots.windowId,0);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);renderHoveredToolTip(x,y);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,InfuserDialog.STYLE.body);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+InfuserDialog.STYLE.headerHeight,InfuserDialog.STYLE.header);for(net.minecraft.inventory.Slot slot:inventorySlots.inventorySlots){int sx=guiLeft+slot.xPos,sy=guiTop+slot.yPos;drawRect(sx-1,sy-1,sx+17,sy+17,InfuserDialog.FRAME);drawRect(sx,sy,sx+16,sy+16,InfuserDialog.SLOT);}}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(container.tile.scanner()?"Scanner":"Printer",12,10,0xffffff);DialogHeader.logo(0,0,xSize,InfuserDialog.STYLE.logoHeaderHeight);fontRenderer.drawString("Chip",36,60,InfuserDialog.TEXT_COLOR);fontRenderer.drawString(container.tile.scanner()?"Original":"Ink sac",104,60,InfuserDialog.TEXT_COLOR);if(!container.tile.scanner())fontRenderer.drawString("Paper",176,60,InfuserDialog.TEXT_COLOR);fontRenderer.drawString("Inventory",100,112,InfuserDialog.TEXT_COLOR);fontRenderer.drawString(container.tile.scanner()?"Original documents remain in the top frame.":"One ink sac + one paper per print.",12,210,InfuserDialog.MUTED_COLOR);}
}
