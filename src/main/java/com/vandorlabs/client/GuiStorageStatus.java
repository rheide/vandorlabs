package com.vandorlabs.client;

import com.vandorlabs.container.ContainerStorageStatus;
import com.vandorlabs.tiles.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fluids.*;

public final class GuiStorageStatus extends GuiContainer {
    private final ContainerStorageStatus status;
    private final boolean battery;
    public GuiStorageStatus(ContainerStorageStatus status){super(status);this.status=status;battery=status.tile instanceof TileEntityBattery;xSize=270;ySize=190;}
    @Override public void initGui(){super.initGui();buttonList.clear();buttonList.add(new GuiButton(0,guiLeft+70,guiTop+156,180,20,"Done"));}
    @Override protected void actionPerformed(GuiButton button){mc.player.closeScreen();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff18212c);int x=guiLeft+20,y=guiTop+38;drawRect(x-2,y-2,x+30,y+110,0xff98a8b8);drawRect(x,y,x+28,y+108,0xff0c1219);int filled=status.values[1]<=0?0:(int)Math.round(108D*status.values[0]/status.values[1]);drawRect(x,y+108-filled,x+28,y+108,battery?0xff62d884:0xff000000|status.values[2]);}
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){fontRenderer.drawString(battery?"Battery Block":"Hexagonal Fuel Tank",16,14,0xffffff);String unit=battery?" RF":" mB";fontRenderer.drawString(status.values[0]+" / "+status.values[1]+unit,64,44,0xffffff);fontRenderer.drawString(String.format(java.util.Locale.ROOT,"%.1f%% full",status.values[1]<=0?0:100D*status.values[0]/status.values[1]),64,64,0xcdd7e4);FluidStack contents=battery?null:((TileEntityHexTank)status.tile).getTankProperties()[0].getContents();Fluid fluid=contents==null?null:contents.getFluid();fontRenderer.drawSplitString(battery?"Green port: RF input\nOrange port: RF output":status.values[0]==0?"Empty":fluid==null?"Mixed fluids":fluid.getLocalizedName(new FluidStack(fluid,1)),64,88,xSize-80,0xcdd7e4);if(!battery)fontRenderer.drawSplitString("Use the Configurizer to change the casing.",64,119,xSize-80,0x96a8ba);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
