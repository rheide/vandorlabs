package com.vandorlabs.client;

import com.vandorlabs.tiles.FrameAppearance;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import java.io.IOException;

/** Shared frame page used by glass, walls, doors and programmable displays. */
public final class GuiFrameAppearance extends GuiMaterialPicker {
    private final TileEntity owner;private final FrameAppearance appearance;private final GuiScreen parent;
    private HousingTextureList textures;private int page;private ProgrammableDialogLayout layout;
    public GuiFrameAppearance(Container container,TileEntity owner,GuiScreen parent){super(container);this.owner=owner;this.parent=parent;appearance=MessageFrameAppearance.appearance(owner);if(FrameAppearance.displayBlock(owner.getBlockType()))appearance.artworkVersion=1;if(!frontAvailable())page=1;}
    private boolean frontAvailable(){return !(owner.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableWall)||owner.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalGlass;}
    private boolean displayAvailable(){return owner instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector&&!(owner.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableWall);}
    private GuiButton pageTab(int id,int index,int count,String label){int span=(xSize-24)/count;return new DialogTabButton(id,guiLeft+12+index*span,guiTop+30,span-2,18,label);}
    protected HousingTextureList materialPicker(){return textures;}
    public void initGui(){layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;super.initGui();buttonList.clear();
        boolean display=displayAvailable(),front=frontAvailable();int count=display?4:front?2:1;if(front)buttonList.add(pageTab(0,0,count,"Front"));buttonList.add(pageTab(1,front?1:0,count,"Interior"));if(display){buttonList.add(pageTab(5,2,count,"Top"));buttonList.add(pageTab(6,3,count,"Bottom"));}
        for(GuiButton b:buttonList)b.enabled=b.id!=(page<2?page:page+3);
        textures=(page<2?HousingTextureList.forFrames(layout.listX,guiTop+54,layout.listWidth,page==0?appearance.front:appearance.interior,8):HousingTextureList.forCategory("Screens",layout.listX,guiTop+54,layout.listWidth,page==2?appearance.splitTop:appearance.splitBottom,8)).visibleRows(Math.max(2,(ySize-64)/HousingTextureList.ROW_HEIGHT)).keepView(textures);
        if(owner instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass||owner instanceof com.vandorlabs.tiles.TileEntitySpaceDoor||owner.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalGlass){boolean on=owner instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass?((com.vandorlabs.tiles.TileEntityProgrammableGlass)owner).isFramed():owner instanceof com.vandorlabs.tiles.TileEntitySpaceDoor?((com.vandorlabs.tiles.TileEntitySpaceDoor)owner).isFramed():((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)owner).isGlassFramed();buttonList.add(layout.control(8,126,on?"Framed":"Bare"));}
        if(front)buttonList.add(layout.control(3,54,"Frame: "+(appearance.tile?"Tile":"Fit")));if(display)buttonList.add(layout.control(4,78,"Display: "+(appearance.displayFit?"Fit":"Tile")));if(display)buttonList.add(layout.control(7,102,"Split display: "+(appearance.split?"On":"Off")));buttonList.add(layout.done(2));
    }
    protected void actionPerformed(GuiButton b){if(b.id<2||b.id==5||b.id==6){page=b.id<2?b.id:b.id-3;initGui();}else if(b.id==2){mc.displayGuiScreen(parent);}else if(b.id==8){if(parent instanceof GuiProgrammableGlass)((GuiProgrammableGlass)parent).actionPerformed(new GuiButton(5,0,0,""));else if(parent instanceof GuiSpaceDoor)((GuiSpaceDoor)parent).actionPerformed(new GuiButton(12,0,0,""));else if(parent instanceof GuiProgrammableWall)((GuiProgrammableWall)parent).actionPerformed(new GuiButton(114,0,0,""));initGui();}else if(b.id==7){appearance.split=!appearance.split;send();initGui();}else if(b.id==4){appearance.displayFit=!appearance.displayFit;send();initGui();}else if(b.id==3){appearance.tile=!appearance.tile;send();initGui();}}
    private void send(){owner.markDirty();com.vandorlabs.tiles.GlassMaterials.changed();if(owner.getWorld()!=null)owner.getWorld().markBlockRangeForRenderUpdate(owner.getPos().add(-1,-1,-1),owner.getPos().add(1,1,1));PacketHandler.INSTANCE.sendToServer(new MessageFrameAppearance(owner,appearance));}
    protected void mouseClicked(int x,int y,int b)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,b,this::actionPerformed,3,4,7,8))return;if(textures.click(x,y,b)){if(textures.picked()){if(page==0)appearance.front=textures.selected();else if(page==1)appearance.interior=textures.selected();else if(page==2)appearance.splitTop=textures.selected();else appearance.splitBottom=textures.selected();send();}return;}super.mouseClicked(x,y,b);}
    protected void drawGuiContainerBackgroundLayer(float p,int x,int y){}
    public void drawScreen(int x,int y,float p){drawDefaultBackground();drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff101012);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+20,0xff202028);fontRenderer.drawString("Frame",guiLeft+12,guiTop+7,0xffffff);DialogHeader.logo(guiLeft,guiTop,xSize);textures.draw(fontRenderer,x,y);super.drawScreen(x,y,p);}
    public boolean doesGuiPauseGame(){return false;}
}
