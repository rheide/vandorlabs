package com.vandorlabs.client;

import com.vandorlabs.container.ContainerProgrammableGlass;
import com.vandorlabs.network.MessageProgrammableGlass;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Mouse;
import java.io.IOException;

/** Glass finishes use the same scrollable thumbnail picker as programmable walls. */
public final class GuiProgrammableGlass extends GuiContainer {
    private final TileEntityProgrammableGlass tile;
    private int size,shade,tint;
    private boolean join,framed;
    private HousingTextureList textures;
    public GuiProgrammableGlass(TileEntityProgrammableGlass tile) {
        super(new ContainerProgrammableGlass(tile));this.tile=tile;
        size=tile.getSize();shade=tile.getShade();join=tile.isJoin();tint=tile.getTint();framed=tile.isFramed();
    }
    @Override public void initGui() {
        ProgrammableDialogLayout layout=new ProgrammableDialogLayout(width,height);
        xSize=layout.width;ySize=layout.height;super.initGui();buttonList.clear();
        textures=HousingTextureList.forGlass(size,guiLeft+12,guiTop+30,layout.controlsX-guiLeft-31,shade)
                .visibleRows(Math.max(2,(ySize-64)/HousingTextureList.ROW_HEIGHT));
        buttonList.add(new GuiButton(0,layout.controlsX,guiTop+30,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Size: "+(size==0?"Small":"Large")));
        buttonList.add(new GuiButton(3,layout.controlsX,guiTop+54,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Join: "+(join?"On":"Off")));
        buttonList.add(new GuiButton(4,layout.controlsX,guiTop+78,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Tint: "+com.vandorlabs.tiles.GlassTextures.tintLabel(tint)));
        buttonList.add(new GuiButton(5,layout.controlsX,guiTop+102,ProgrammableDialogLayout.CONTROLS_WIDTH,20,framed?"Framed":"Bare"));
        buttonList.add(new GuiButton(2,layout.controlsX,guiTop+ySize-26,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Done"));
    }
    void choose(int choice){shade=choice;send();}
    private void send(){tile.setSize(size);tile.setShade(shade);tile.setJoin(join);tile.setAppearance(tint,framed);PacketHandler.INSTANCE.sendToServer(new MessageProgrammableGlass(tile.getPos(),size,shade,join).withAppearance(tint,framed));}
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==2){mc.player.closeScreen();return;}
        if(button.id==0)size=GuiOptionCycle.next(size,2);
        if(button.id==3)join=!join;
        if(button.id==4)tint=GuiOptionCycle.next(tint,3);
        if(button.id==5)framed=!framed;
        send();initGui();
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0,3,4,5))return;
        if(textures.click(x,y,button)){if(textures.picked()){shade=textures.selected();send();}return;}
        super.mouseClicked(x,y,button);
    }
    @Override protected void mouseReleased(int x,int y,int button){textures.release();super.mouseReleased(x,y,button);}
    @Override protected void mouseClickMove(int x,int y,int button,long time){if(!textures.drag(y))super.mouseClickMove(x,y,button,time);}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();textures.wheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel());}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){}
    @Override public void drawScreen(int x,int y,float partial){
        drawDefaultBackground();drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF101012);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+18,0xFF202028);
        fontRenderer.drawString("Programmable Glass",guiLeft+8,guiTop+5,0xFFFFFFFF);
        textures.draw(fontRenderer,x,y);super.drawScreen(x,y,partial);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
