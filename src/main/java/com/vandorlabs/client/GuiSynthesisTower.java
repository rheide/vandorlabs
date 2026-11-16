package com.vandorlabs.client;

import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.container.ContainerSynthesisTower;
import com.vandorlabs.network.*;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.*;
import net.minecraft.entity.player.InventoryPlayer;
import java.io.IOException;

/** Production selection, full textured design preview and ordinary furnace-style slots. */
public final class GuiSynthesisTower extends GuiContainer {
    private final ContainerSynthesisTower container;
    private final VisualClock clock=new VisualClock();
    private int page,activation,recipeScroll;
    private boolean draggingRecipes;
    private java.util.List<String> recipes(){return container.tile.builderRecipes();}
    private int rows(){return layout.visibleRows();}
    private void clampScroll(){recipeScroll=Math.max(0,Math.min(recipeScroll,Math.max(0,recipes().size()-rows())));}
    private boolean compact;
    private com.vandorlabs.ui.SynthesisDialog.Layout layout=new com.vandorlabs.ui.SynthesisDialog.Layout(false);
    private int machineY(){return layout.machineY;}
    private int inventoryY(){return layout.inventoryY;}
    private int hotbarY(){return layout.hotbarY;}
    int previewTop(){return guiTop+layout.previewTop;}
    int previewBottom(){return guiTop+layout.previewBottom;}
    int previewLeft(){return guiLeft+layout.previewLeft;}
    int dialogLeft(){return guiLeft;}
    int dialogTop(){return guiTop;}
    boolean controlsFit(){
        if(guiLeft<0||guiTop<0||guiLeft+xSize>width||guiTop+ySize>height)return false;
        for(net.minecraft.inventory.Slot slot:inventorySlots.inventorySlots)if(slot.xPos<0||slot.xPos+16>xSize||slot.yPos<0||slot.yPos+16>ySize)return false;
        return receiver.button().y+receiver.button().height<=guiTop+inventoryY()-10;
    }
    private ReceiverPane receiver;
    private com.vandorlabs.redstone.ChannelList channels;
    public GuiSynthesisTower(InventoryPlayer inventory,TileEntityConstructor tile){super(new ContainerSynthesisTower(inventory,tile));container=(ContainerSynthesisTower)inventorySlots;xSize=420;ySize=306;activation=tile.synthesisActivation();channels=tile.getRedstoneChannels();}
    @Override public void initGui(){
        layout=com.vandorlabs.ui.SynthesisDialog.layout(width,height);compact=layout.compact;xSize=layout.width;ySize=layout.height;super.initGui();
        for(int i=0;i<inventorySlots.inventorySlots.size();i++){net.minecraft.inventory.Slot slot=inventorySlots.inventorySlots.get(i);slot.xPos=layout.slotX(i);slot.yPos=layout.slotY(i);}
        draggingRecipes=false;buttonList.clear();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        buttonList.add(new DialogTabButton(100,guiLeft+12,guiTop+32,layout.tabWidth(),20,"Production"));
        buttonList.add(new DialogTabButton(101,guiLeft+layout.secondTabX(),guiTop+32,layout.tabWidth(),20,"Redstone"));
        for(GuiButton b:buttonList)b.enabled=b.id!=100+page;
        receiver=new ReceiverPane(40,2,fontRenderer,guiLeft+12,guiTop+layout.receiverY(),layout.receiverWidth(),channels,activation);
        receiver.setVisible(page==1);buttonList.add(receiver.button());
    }
    private boolean saveRedstone(){
        com.vandorlabs.redstone.ChannelList parsed=receiver.channels();if(parsed==null)return false;
        channels=parsed;PacketHandler.INSTANCE.sendToServer(new MessageSynthesisRedstone(inventorySlots.windowId,container.tile.getPos(),activation,channels));return true;
    }
    @Override protected void actionPerformed(GuiButton button){
        if(button.id==100||button.id==101){if(page==1&&!saveRedstone())return;page=button.id-100;initGui();}
        else if(button.id==2){activation=GuiOptionCycle.next(activation,3);receiver.setMode(activation);saveRedstone();}
    }
    @Override protected void keyTyped(char c,int key)throws IOException{if(page==1&&receiver.keyTyped(c,key)){saveRedstone();return;}super.keyTyped(c,key);}
    @Override public void onGuiClosed(){if(page==1&&receiver!=null)saveRedstone();org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override public void updateScreen(){super.updateScreen();if(receiver!=null)receiver.field().updateCursorCounter();}
    @Override public void drawScreen(int x,int y,float partial){
        drawDefaultBackground();super.drawScreen(x,y,partial);
        if(page==1){GlStateManager.disableLighting();GlStateManager.disableDepth();receiver.draw();GlStateManager.enableDepth();}
        if(isPointInRegion(layout.machineX(3),layout.slotY(3),16,16,x,y))drawHoveringText(com.vandorlabs.ui.SynthesisDialog.CATALOG_CHIP_TOOLTIP,x,y);
        else if(isPointInRegion(layout.rfX,layout.rfY,layout.rfWidth,layout.rfHeight,x,y))drawHoveringText(com.vandorlabs.ui.SynthesisDialog.rfTooltip(container.values[2],container.values[3]),x,y);
        else if(page==0&&isPointInRegion(layout.previewLeft,layout.previewTop-12,layout.previewRight-layout.previewLeft,10,x,y)&&fontRenderer.getStringWidth(selectedLabel())>layout.previewRight-layout.previewLeft)drawHoveringText(selectedLabel(),x,y);
        else renderHoveredToolTip(x,y);
    }
    private String selectedLabel(){return com.vandorlabs.client.constructors.BuilderRecipePreview.label(container.tile.builderRecipe(container.values[0]));}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,com.vandorlabs.ui.SynthesisDialog.STYLE.body);DialogHeader.logo(guiLeft,guiTop,xSize);
        drawRect(guiLeft+12,guiTop+52,guiLeft+xSize-12,guiTop+layout.machineY+20,0xFF17212C);
        if(page==0){
        drawRect(guiLeft+layout.listLeft,guiTop+layout.listTop,guiLeft+layout.listRight,guiTop+layout.listBottom,0xFF0D1118);
        clampScroll();int selected=container.values[0];java.util.List<String> recipes=recipes();
        for(int i=0;i<Math.min(rows(),recipes.size()-recipeScroll);i++){
            String blueprint=recipes.get(recipeScroll+i);
            int y=guiTop+layout.listTop+i*layout.rowHeight;
            if(container.tile.builderRecipeIndex(blueprint)==selected)drawRect(guiLeft+layout.listLeft+2,y,guiLeft+layout.listRight-8,y+layout.rowHeight-1,0xFF345166);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(com.vandorlabs.client.constructors.BuilderRecipePreview.label(blueprint),layout.listRight-layout.listLeft-20),guiLeft+layout.listLeft+6,y+(layout.rowHeight-8)/2,container.tile.builderRecipeIndex(blueprint)==selected?0xFFFFFF:0xB6C2CD);
        }
        drawRect(guiLeft+layout.previewLeft,previewTop(),guiLeft+layout.previewRight,previewBottom(),0xFF0D1118);
        if(recipes.size()>rows()){int track=layout.listBottom-layout.listTop,thumb=Math.max(12,track*rows()/recipes.size()),offset=(track-thumb)*recipeScroll/(recipes.size()-rows());drawRect(guiLeft+layout.listRight-6,guiTop+layout.listTop,guiLeft+layout.listRight,guiTop+layout.listBottom,0xFF24323E);drawRect(guiLeft+layout.listRight-6,guiTop+layout.listTop+offset,guiLeft+layout.listRight,guiTop+layout.listTop+offset+thumb,0xFF809AA8);}
        String blueprint=container.tile.builderRecipe(selected);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(guiLeft+(layout.previewLeft+layout.previewRight)/2,guiTop+(layout.previewTop+layout.previewBottom)/2,90);
            double previewScale=Math.min(layout.previewRight-layout.previewLeft,layout.previewBottom-layout.previewTop)*.8/com.vandorlabs.client.constructors.BuilderRecipePreview.extent(blueprint);GlStateManager.scale(previewScale,-previewScale,previewScale);GlStateManager.rotate(17,1,0,0);
            com.vandorlabs.client.constructors.BuilderRecipePreview.render(blueprint,(float)(clock.sample(System.nanoTime(),mc.isGamePaused())*1.5));
        }finally{GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);RenderHelper.enableGUIStandardItemLighting();}
        }
        for(int i=0;i<7;i++)slot(layout.slotX(i),layout.slotY(i));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(layout.inventoryX+col*18,inventoryY()+row*18);
        for(int col=0;col<9;col++)slot(layout.inventoryX+col*18,hotbarY());
        drawRect(guiLeft+layout.rfX-1,guiTop+layout.rfY-1,guiLeft+layout.rfX+layout.rfWidth+1,guiTop+layout.rfY+layout.rfHeight+1,0xFF67727E);
        drawRect(guiLeft+layout.rfX,guiTop+layout.rfY,guiLeft+layout.rfX+layout.rfWidth,guiTop+layout.rfY+layout.rfHeight,0xFF0B1017);
        int stored=Math.max(0,container.values[2]),capacity=Math.max(0,container.values[3]);
        int fill=capacity==0?0:(int)((long)Math.min(stored,capacity)*layout.rfWidth/capacity);
        if(fill>0)drawRect(guiLeft+layout.rfX,guiTop+layout.rfY,guiLeft+layout.rfX+fill,guiTop+layout.rfY+layout.rfHeight,0xFF55CBA4);
        drawRect(guiLeft+layout.progressX(),guiTop+machineY()+4,guiLeft+layout.progressX()+layout.progressWidth(),guiTop+machineY()+12,0xFF303947);
        int progress=Math.max(0,Math.min(200,container.values[1]));
        drawRect(guiLeft+layout.progressX(),guiTop+machineY()+4,guiLeft+layout.progressX()+layout.progressWidth()*progress/200,guiTop+machineY()+12,0xFF55CBA4);
    }
    private void slot(int x,int y){drawRect(guiLeft+x-1,guiTop+y-1,guiLeft+x+17,guiTop+y+17,0xFF67727E);drawRect(guiLeft+x,guiTop+y,guiLeft+x+16,guiTop+y+16,0xFF0B1017);}
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){
        fontRenderer.drawString(container.tile.builderMachine().equals("assembly_press")?"Assembly Press":container.tile.builderMachine().equals("enclosed_gantry")?"Enclosed Gantry":"Synthesis Tower",12,10,0xFFFFFF);
        if(page==0){fontRenderer.drawString("Designs",layout.listLeft,layout.listHeadingY(),0xD4DFE9);fontRenderer.drawString(fontRenderer.trimStringToWidth(selectedLabel(),layout.previewRight-layout.previewLeft),layout.previewLeft,layout.previewTop-12,0xD4DFE9);}
        if(page==1&&!compact){fontRenderer.drawString("Physical Redstone or any receiving channel.",12,180,0xA7B3C0);fontRenderer.drawString("A disabled machine keeps its current build.",12,193,0xA7B3C0);}
        fontRenderer.drawString(container.tile.builderMachine().equals("assembly_press")?"Ingots":"Matter",layout.machineX(0)-6,machineY()-12,0xC3CFDB);fontRenderer.drawString("Output",layout.machineX(1)-4,machineY()-12,0xC3CFDB);
        fontRenderer.drawString("Chip",layout.machineX(3)-4,machineY()-12,0xC3CFDB);
        fontRenderer.drawString("RF",layout.rfX+4,layout.rfY+4,0xE4EDF4);
        fontRenderer.drawString(">",layout.progressX()+layout.progressWidth()/2-2,machineY()+4,0xD4DFE9);
        fontRenderer.drawString("Inventory",layout.inventoryX,inventoryY()-10,0xD4DFE9);
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(page==1){receiver.mouseClicked(x,y,button);if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,2))return;}
        if(page==0&&button==0&&recipes().size()>rows()&&x>=guiLeft+layout.listRight-6&&x<guiLeft+layout.listRight&&y>=guiTop+layout.listTop&&y<guiTop+layout.listBottom){draggingRecipes=true;scrollAt(y);return;}
        if(page==0&&(button==0||button==1)&&x>=guiLeft+layout.listLeft&&x<guiLeft+layout.listRight-6&&y>=guiTop+layout.listTop&&y<guiTop+layout.listTop+rows()*layout.rowHeight){
            int choice=(y-guiTop-layout.listTop)/layout.rowHeight+recipeScroll;
            if(choice<recipes().size())PacketHandler.INSTANCE.sendToServer(new MessageSynthesisTower(inventorySlots.windowId,container.tile.getPos(),container.tile.builderRecipeIndex(recipes().get(choice))));return;
        }
        super.mouseClicked(x,y,button);
    }
    private void scrollAt(int y){int range=Math.max(0,recipes().size()-rows());int track=layout.listBottom-layout.listTop,thumb=Math.max(12,track*rows()/Math.max(1,recipes().size()));recipeScroll=(int)Math.round((y-guiTop-layout.listTop-thumb/2D)*range/Math.max(1,track-thumb));clampScroll();}
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed){if(draggingRecipes)scrollAt(y);else super.mouseClickMove(x,y,button,elapsed);}
    @Override protected void mouseReleased(int x,int y,int button){draggingRecipes=false;super.mouseReleased(x,y,button);}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=org.lwjgl.input.Mouse.getEventDWheel();int mouseX=org.lwjgl.input.Mouse.getEventX()*width/mc.displayWidth,mouseY=height-org.lwjgl.input.Mouse.getEventY()*height/mc.displayHeight-1;if(page==0&&wheel!=0&&mouseX>=guiLeft+layout.listLeft&&mouseX<guiLeft+layout.listRight&&mouseY>=guiTop+layout.listTop&&mouseY<guiTop+layout.listBottom){recipeScroll+=wheel>0?-1:1;clampScroll();}}

}
