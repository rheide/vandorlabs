package com.vandorlabs.client;

import com.vandorlabs.container.ContainerProgrammableGlass;
import com.vandorlabs.network.MessageProgrammableGlass;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.gui.GuiButton;
import java.io.IOException;

/** Glass finishes use the same scrollable thumbnail picker as programmable walls. */
public final class GuiProgrammableGlass extends GuiMaterialPicker {
    private final TileEntityProgrammableGlass tile;
    private int size,shade,tint,material,textureLayout;
    private boolean join,framed,stretch;
    private HousingTextureList textures;
    public GuiProgrammableGlass(TileEntityProgrammableGlass tile) {
        super(new ContainerProgrammableGlass(tile));this.tile=tile;
        textureLayout=tile.getTextureLayout();size=0;shade=tile.getShade();join=tile.isJoin();tint=tile.getTint();framed=tile.isFramed();material=tile.getMaterial();stretch=tile.isStretch();
    }
    @Override protected HousingTextureList materialPicker(){return textures;}
    @Override public void initGui() {
        ProgrammableDialogLayout layout=new ProgrammableDialogLayout(width,height);
        xSize=layout.width;ySize=layout.height;super.initGui();buttonList.clear();buttonList.add(new DialogTabButton(197,guiLeft+xSize-96,guiTop+1,58,17,"Frame"));
        textures=HousingTextureList.forGlassMaterials(size,guiLeft+12,guiTop+30,layout.controlsX-guiLeft-31,shade,material)
                .visibleRows(Math.max(2,(ySize-64)/HousingTextureList.ROW_HEIGHT)).keepView(textures);
        buttonList.add(new GuiButton(3,layout.controlsX,guiTop+54,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Join: "+(join?"On":"Off")));
        buttonList.add(new GuiButton(4,layout.controlsX,guiTop+78,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Tint: "+com.vandorlabs.tiles.GlassTextures.tintLabel(tint)));
        GuiButton framedButton=new GuiButton(5,layout.controlsX,guiTop+102,ProgrammableDialogLayout.CONTROLS_WIDTH,20,framed?"Framed":"Bare");framedButton.visible=false;buttonList.add(framedButton);
        if(material>=0)buttonList.add(new GuiButton(6,layout.controlsX,guiTop+126,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(textureLayout)));
        buttonList.add(new GuiButton(2,layout.controlsX,guiTop+ySize-26,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Done"));
    }
    void choose(int choice){if(textures!=null && textures.selected()!=choice)textures.setSelected(choice);if(choice>=HousingTextureList.CANOPY_GLASS_BASE && choice<HousingTextureList.CANOPY_GLASS_BASE+com.vandorlabs.tiles.GlassTextures.IDS.length){shade=choice-HousingTextureList.CANOPY_GLASS_BASE;material=-1;}else material=choice;send();initGui();}
    private void send(){tile.setSize(size);tile.setShade(shade);tile.setMaterial(material);tile.setTextureLayout(textureLayout);tile.setJoin(join);tile.setAppearance(tint,framed);PacketHandler.INSTANCE.sendToServer(new MessageProgrammableGlass(tile.getPos(),size,shade,join).withAppearance(tint,framed).withMaterial(material).withTextureLayout(textureLayout));}
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==197){mc.displayGuiScreen(new GuiFrameAppearance(inventorySlots,tile,this));return;}

        if(button.id==2){mc.player.closeScreen();return;}
        if(button.id==3)join=!join;
        if(button.id==4)tint=GuiOptionCycle.next(tint,3);
        if(button.id==5)framed=!framed;
        if(button.id==6){textureLayout=GuiOptionCycle.next(textureLayout,3);stretch=textureLayout!=1;}
        send();initGui();
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0,3,4,5,6))return;
        if(textures.click(x,y,button)){if(textures.picked())choose(textures.selected());return;}
        super.mouseClicked(x,y,button);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){}
    @Override public void drawScreen(int x,int y,float partial){
        drawDefaultBackground();drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF101012);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+18,0xFF202028);
        fontRenderer.drawString("Programmable Glass",guiLeft+8,guiTop+5,0xFFFFFFFF);
        textures.draw(fontRenderer,x,y);DialogHeader.logo(guiLeft,guiTop,xSize);super.drawScreen(x,y,partial);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
