package com.vandorlabs.client;

import com.vandorlabs.container.ContainerStorageStatus;
import com.vandorlabs.tiles.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.fluids.*;

public final class GuiStorageStatus extends GuiMaterialPicker {
    private final ContainerStorageStatus status;
    private final boolean battery;
    private HousingTextureList textures;
    private int controls;
    public GuiStorageStatus(ContainerStorageStatus status){super(status);this.status=status;battery=status.tile instanceof TileEntityBattery;xSize=270;ySize=190;}
    @Override protected HousingTextureList materialPicker(){return textures;}
    @Override public void initGui(){
        xSize=Math.min(354,width-16);ySize=Math.min(238,height-16);super.initGui();buttonList.clear();
        controls=xSize/2+14;
        textures=new HousingTextureList(guiLeft+12,guiTop+38,xSize/2-22,battery?((TileEntityBattery)status.tile).getHousingTexture():((TileEntityHexTank)status.tile).getHousingTexture()).visibleRows((ySize-62)/HousingTextureList.ROW_HEIGHT).keepView(textures).custom(this::choose);
        if(!battery){
            buttonList.add(new GuiButton(1,guiLeft+controls,guiTop+ySize-94,xSize-controls-12,20,shapeLabel()));
            buttonList.add(new GuiButton(2,guiLeft+controls,guiTop+ySize-66,xSize-controls-12,20,layoutLabel()));
        }
        if(battery)buttonList.add(new GuiButton(1,guiLeft+controls,guiTop+ySize-66,xSize-controls-12,20,shapeLabel()));
        buttonList.add(new GuiButton(0,guiLeft+controls,guiTop+ySize-32,xSize-controls-12,20,"Done"));
    }
    private void choose(int casing){if(battery){TileEntityBattery cell=(TileEntityBattery)status.tile;cell.setHousingTexture(casing);com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageTankShape(status.windowId,cell.isBlockShape()).withCasing(casing));return;}TileEntityHexTank tank=(TileEntityHexTank)status.tile;tank.setHousingTexture(casing);com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageTankShape(status.windowId,tank.isBlockShape()).withCasing(casing));}
    @Override protected void mouseClicked(int x,int y,int button)throws java.io.IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,2))return;if(textures!=null){int before=textures.selected();if(textures.click(x,y,button)){if(before!=textures.selected())choose(textures.selected());return;}}super.mouseClicked(x,y,button);}
    private String layoutLabel(){return "Texture: "+(((TileEntityHexTank)status.tile).getMainTextureLayout()==1?"Tile":"Fit");}
    private String shapeLabel(){return "Shape: "+((battery?((TileEntityBattery)status.tile).isBlockShape():((TileEntityHexTank)status.tile).isBlockShape())?"Block":"Hexagon");}
    @Override public void updateScreen(){super.updateScreen();for(GuiButton b:buttonList)if(b.id==1)b.displayString=shapeLabel();else if(b.id==2)b.displayString=layoutLabel();}
    @Override protected void actionPerformed(GuiButton button){if(button.id==1&&battery){TileEntityBattery tile=(TileEntityBattery)status.tile;tile.setBlockShape(!tile.isBlockShape());com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageTankShape(status.windowId,tile.isBlockShape()));button.displayString=shapeLabel();}else if(button.id==1){TileEntityHexTank tank=(TileEntityHexTank)status.tile;boolean block=!tank.isBlockShape();tank.setBlockShape(block);com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageTankShape(status.windowId,block));button.displayString=shapeLabel();}else if(button.id==2){TileEntityHexTank tank=(TileEntityHexTank)status.tile;int layout=tank.getMainTextureLayout()==1?2:1;tank.setMainTextureLayout(layout);com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageTankShape(status.windowId,tank.isBlockShape()).withLayout(layout));button.displayString=layoutLabel();}else mc.player.closeScreen();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff18212c);int x=guiLeft+controls,y=guiTop+38,w=18,h=88;drawRect(x-2,y-2,x+w+2,y+h+2,0xff98a8b8);drawRect(x,y,x+w,y+h,0xff0c1219);int filled=status.values[1]<=0?0:(int)Math.round(h*1D*status.values[0]/status.values[1]);drawRect(x,y+h-filled,x+w,y+h,battery?0xff62d884:0xff000000|status.values[2]);if(textures!=null)textures.draw(fontRenderer,mouseX,mouseY);}
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){fontRenderer.drawString(battery?"Battery Block":"Fuel Tank",16,14,0xffffff);fontRenderer.drawString("Casing",12,26,0x96a8ba);String unit=battery?" RF":" mB";int text=controls+28,span=xSize-text-12;fontRenderer.drawSplitString(status.values[0]+" / "+status.values[1]+unit,text,42,span,0xffffff);fontRenderer.drawString(String.format(java.util.Locale.ROOT,"%.1f%% full",status.values[1]<=0?0:100D*status.values[0]/status.values[1]),text,76,0xcdd7e4);FluidStack contents=battery?null:((TileEntityHexTank)status.tile).getTankProperties()[0].getContents();Fluid fluid=contents==null?null:contents.getFluid();fontRenderer.drawSplitString(battery?"Green port: RF input\nOrange port: RF output":status.values[0]==0?"Empty":fluid==null?"Mixed fluids":fluid.getLocalizedName(new FluidStack(fluid,1)),text,98,span,0xcdd7e4);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
