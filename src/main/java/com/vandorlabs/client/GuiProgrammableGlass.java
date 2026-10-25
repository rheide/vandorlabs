package com.vandorlabs.client;

import com.vandorlabs.container.ContainerProgrammableGlass;
import com.vandorlabs.network.MessageProgrammableGlass;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.gui.GuiButton;
import java.io.IOException;
import com.vandorlabs.ui.GlassDialog;

/** Glass finishes use the same scrollable thumbnail picker as programmable walls. */
public final class GuiProgrammableGlass extends GuiMaterialPicker {
    private final TileEntityProgrammableGlass tile;
    private GlassDialog.State settings;
    private HousingTextureList textures;
    public GuiProgrammableGlass(TileEntityProgrammableGlass tile) {
        super(new ContainerProgrammableGlass(tile));this.tile=tile;
        settings=GlassDialog.State.edit(tile.settings());
    }
    @Override protected HousingTextureList materialPicker(){return textures;}
    @Override public void initGui() {
        ProgrammableDialogLayout layout=new ProgrammableDialogLayout(width,height);
        xSize=layout.width;ySize=layout.height;super.initGui();buttonList.clear();
        textures=HousingTextureList.forGlassMaterials(settings.size,layout.listX,GlassDialog.listY(layout),layout.listWidth,settings.shade,settings.material)
                .visibleRows(GlassDialog.rows(layout)).keepView(textures);
        for(ProgrammableDialogLayout.Button button:GlassDialog.controls(layout,settings))buttonList.add(new GuiButton(button.id,button.x,button.y,button.width,button.height,button.label));
    }
    void choose(int choice){if(textures!=null&&textures.selected()!=choice)textures.setSelected(choice);settings=settings.choose(choice);send();initGui();}
    private void send(){tile.setSize(settings.size);tile.setShade(settings.shade);tile.setMaterial(settings.material);tile.setTextureLayout(settings.layout);tile.setJoin(settings.join);tile.setAppearance(settings.tint,settings.framed);PacketHandler.INSTANCE.sendToServer(new MessageProgrammableGlass(tile.getPos(),settings.size,settings.shade,settings.join).withAppearance(settings.tint,settings.framed).withMaterial(settings.material).withTextureLayout(settings.layout));}
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==2){mc.player.closeScreen();return;}
        settings=settings.action(button.id,GuiOptionCycle.direction());
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
        fontRenderer.drawString(GlassDialog.TITLE,guiLeft+8,guiTop+5,0xFFFFFFFF);
        textures.draw(fontRenderer,x,y);super.drawScreen(x,y,partial);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
