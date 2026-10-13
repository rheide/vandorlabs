package com.vandorlabs.client;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.container.ContainerLargeCanopy;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fml.client.config.GuiSlider;
import org.lwjgl.input.Mouse;
import java.io.IOException;
/** Live palette and geometry controls; server validates every proposed footprint. */
public final class GuiLargeCanopy extends GuiContainer {
    private final TileEntityCanopy tile;private int depth,scale,shade,tint;private HousingTextureList textures;private String sent;private boolean ready;private MessageCanopySettingsResult pending;private String error="";
    public GuiLargeCanopy(TileEntityCanopy tile){super(new ContainerLargeCanopy(tile));this.tile=tile.owner();depth=this.tile.large.depth;scale=this.tile.large.scale;shade=this.tile.large.shade;tint=this.tile.large.tint;sent=key();}
    private String key(){return depth+":"+scale+":"+shade+":"+tint;}
    void apply(){if(ready && !key().equals(sent)){sent=key();PacketHandler.INSTANCE.sendToServer(new MessageLargeCanopy(tile.getPos(),depth,scale,shade,tint));}}
    public void acknowledge(MessageCanopySettingsResult result){if(tile.getPos().equals(result.pos) && result.requested.equals(sent)){pending=result;reconcile();}}
    private void reconcile(){
        if(pending==null || Mouse.isButtonDown(0))return;
        MessageCanopySettingsResult result=pending;pending=null;if(!result.requested.equals(sent))return;
        if(result.applied){error="";return;}
        depth=result.depth;scale=result.scale;shade=result.shade;tint=result.tint;sent=key();error="The new surface is obstructed or protected.";initGui();
    }
    @Override public void updateScreen(){super.updateScreen();reconcile();}
    @Override public void initGui(){ProgrammableDialogLayout layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;super.initGui();buttonList.clear();ready=false;
        textures=HousingTextureList.forGlass(1,guiLeft+12,guiTop+30,layout.controlsX-guiLeft-31,shade).visibleRows(Math.max(2,(ySize-64)/HousingTextureList.ROW_HEIGHT));
        if(tile.resizable()){buttonList.add(new GuiSlider(0,layout.controlsX,guiTop+30,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Depth: "," px",0,80,depth,false,true,slider->{depth=slider.getValueInt();apply();}));
        buttonList.add(new GuiSlider(1,layout.controlsX,guiTop+54,ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Inner size: ","%",10,100,scale,false,true,slider->{scale=slider.getValueInt();apply();}));
        }buttonList.add(new GuiButton(2,layout.controlsX,guiTop+(tile.resizable()?78:30),ProgrammableDialogLayout.CONTROLS_WIDTH,20,"Tint: "+GlassTextures.tintLabel(tint)));
        ready=true;}
    @Override protected void actionPerformed(GuiButton button){if(button.id==2){tint=GuiOptionCycle.next(tint,3);button.displayString="Tint: "+GlassTextures.tintLabel(tint);apply();}}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,2))return;if(textures.click(x,y,button)){if(textures.picked()){shade=textures.selected();apply();}return;}super.mouseClicked(x,y,button);}
    @Override protected void mouseReleased(int x,int y,int button){textures.release();super.mouseReleased(x,y,button);}
    @Override protected void mouseClickMove(int x,int y,int button,long time){if(!textures.drag(y))super.mouseClickMove(x,y,button,time);}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();textures.wheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel());}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff101012);fontRenderer.drawString(net.minecraft.client.resources.I18n.format(tile.block().getUnlocalizedName()+".name"),guiLeft+8,guiTop+8,0xffffff);textures.draw(fontRenderer,x,y);if(!error.isEmpty())fontRenderer.drawSplitString(error,guiLeft+xSize-ProgrammableDialogLayout.CONTROLS_WIDTH-12,guiTop+ySize-44,ProgrammableDialogLayout.CONTROLS_WIDTH,0xffffaa88);super.drawScreen(x,y,partial);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
