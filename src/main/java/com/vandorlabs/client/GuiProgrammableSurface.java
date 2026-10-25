package com.vandorlabs.client;

import com.vandorlabs.container.ContainerSurface;
import com.vandorlabs.network.*;
import com.vandorlabs.tiles.TileEntityTurntableSurface;
import com.vandorlabs.turntable.TurntableTextures;
import net.minecraft.client.gui.GuiButton;
import java.io.IOException;

/** Surface-local active/inactive artwork uses the standard material picker and scrolling behavior. */
public final class GuiProgrammableSurface extends GuiMaterialPicker {
    private final TileEntityTurntableSurface tile;
    private String on,off;private int layout;private boolean active;
    private HousingTextureList textures;
    public GuiProgrammableSurface(ContainerSurface container){super(container);tile=container.tile;on=tile.surfaceOn;off=tile.surfaceOff;layout=tile.textureLayout;}
    @Override protected HousingTextureList materialPicker(){return textures;}
    @Override public void initGui(){ProgrammableDialogLayout positions=new ProgrammableDialogLayout(width,height);xSize=positions.width;ySize=positions.height;super.initGui();buttonList.clear();
        textures=new HousingTextureList(guiLeft+12,guiTop+30,positions.controlsX-guiLeft-31,TurntableTextures.materialChoice(active?on:off),(ySize-64)/12).keepView(textures);
        buttonList.add(new GuiButton(0,positions.controlsX,guiTop+30,ProgrammableDialogLayout.CONTROLS_WIDTH,20,active?"Active artwork":"Inactive artwork"));
        buttonList.add(new GuiButton(1,positions.controlsX,guiTop+54,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(layout)));
        buttonList.add(new GuiButton(3,positions.controlsX,guiTop+78,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Custom..."));
        buttonList.add(new GuiButton(2,positions.controlsX,guiTop+ySize-26,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Done"));
    }
    void choose(int choice){String selected=TurntableTextures.fromMaterialChoice(choice);if(active)on=selected;else off=selected;send();initGui();}
    private void send(){PacketHandler.INSTANCE.sendToServer(new MessageProgrammableSurface(inventorySlots.windowId,on,off,layout));}
    @Override protected void actionPerformed(GuiButton button){if(button.id==2){mc.player.closeScreen();return;}if(button.id==0){active=!active;initGui();return;}if(button.id==3){mc.displayGuiScreen(new GuiCustomTexture(this,this::choose));return;}if(button.id==1){layout=GuiOptionCycle.next(layout,3);send();initGui();}}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0,1))return;if(textures.click(x,y,button)){if(textures.picked())choose(textures.selected());return;}super.mouseClicked(x,y,button);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff101012);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+18,0xff202028);fontRenderer.drawString("Programmable Surface",guiLeft+8,guiTop+5,0xffffffff);textures.draw(fontRenderer,x,y);super.drawScreen(x,y,partial);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
