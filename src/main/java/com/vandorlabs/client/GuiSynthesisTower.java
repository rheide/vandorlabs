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
    private static final String[] IDS=com.vandorlabs.ui.SynthesisDialog.DESIGNS;
    private static final String[] NAMES=com.vandorlabs.ui.SynthesisDialog.NAMES;
    private final ContainerSynthesisTower container;
    private final VisualClock clock=new VisualClock();
    private int page,activation;
    private boolean compact;
    private com.vandorlabs.ui.SynthesisDialog.Layout layout=new com.vandorlabs.ui.SynthesisDialog.Layout(false);
    private int machineY(){return compact?136:190;}
    private int inventoryY(){return compact?162:224;}
    private int hotbarY(){return compact?220:282;}
    int previewTop(){return guiTop+(compact?58:72);}
    int previewBottom(){return guiTop+(compact?110:158);}
    int previewLeft(){return guiLeft+layout.previewLeft;}
    int dialogLeft(){return guiLeft;}
    int dialogTop(){return guiTop;}
    boolean controlsFit(){
        if(guiLeft<0||guiTop<0||guiLeft+xSize>width||guiTop+ySize>height)return false;
        for(net.minecraft.inventory.Slot slot:inventorySlots.inventorySlots)if(slot.yPos<0||slot.yPos+16>ySize)return false;
        return receiver.button().y+receiver.button().height<=guiTop+machineY()-12;
    }
    private ReceiverPane receiver;
    private com.vandorlabs.redstone.ChannelList channels;
    public GuiSynthesisTower(InventoryPlayer inventory,TileEntityConstructor tile){super(new ContainerSynthesisTower(inventory,tile));container=(ContainerSynthesisTower)inventorySlots;xSize=348;ySize=306;activation=tile.synthesisActivation();channels=tile.getRedstoneChannels();}
    @Override public void initGui(){
        layout=com.vandorlabs.ui.SynthesisDialog.layout(width,height);compact=layout.compact;xSize=layout.width;ySize=layout.height;super.initGui();
        for(int i=0;i<inventorySlots.inventorySlots.size();i++){net.minecraft.inventory.Slot slot=inventorySlots.inventorySlots.get(i);slot.xPos=layout.slotX(i);slot.yPos=layout.slotY(i);}
        buttonList.clear();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        buttonList.add(new DialogTabButton(100,guiLeft+12,guiTop+32,layout.tabWidth(),20,"Production"));
        buttonList.add(new DialogTabButton(101,guiLeft+layout.previewLeft,guiTop+32,layout.tabWidth(),20,"Redstone"));
        for(GuiButton b:buttonList)b.enabled=b.id!=100+page;
        receiver=new ReceiverPane(40,2,fontRenderer,guiLeft+12,guiTop+layout.receiverY(),xSize-24,channels,activation);
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
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);if(page==1){GlStateManager.disableLighting();GlStateManager.disableDepth();receiver.draw();GlStateManager.enableDepth();}if(isPointInRegion(layout.machineX(3),machineY(),16,16,x,y))drawHoveringText(com.vandorlabs.ui.SynthesisDialog.CATALOG_CHIP_TOOLTIP,x,y);else renderHoveredToolTip(x,y);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,com.vandorlabs.ui.SynthesisDialog.STYLE.body);DialogHeader.logo(guiLeft,guiTop,xSize);
        if(page==0){
        drawRect(guiLeft+12,previewTop(),guiLeft+layout.previewLeft-10,previewBottom(),0xFF0D1118);
        int selected=container.values[0];
        for(int i=0;i<3;i++){
            int y=previewTop()+(compact?0:4)+i*(compact?17:24);
            if(i==selected)drawRect(guiLeft+14,y,guiLeft+layout.previewLeft-12,y+(compact?17:22),0xFF345166);
            fontRenderer.drawString(NAMES[i],guiLeft+20,y+(compact?5:9),i==selected?0xFFFFFF:0xB6C2CD);
        }
        drawRect(guiLeft+layout.previewLeft,previewTop(),guiLeft+layout.previewRight,previewBottom(),0xFF0D1118);
        int preview=Math.max(0,Math.min(2,selected));
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(guiLeft+(layout.previewLeft+layout.previewRight)/2,guiTop+(compact?84:115),90);
            double previewScale=compact?8:12;GlStateManager.scale(previewScale,-previewScale,previewScale);GlStateManager.rotate(17,1,0,0);
            com.vandorlabs.client.constructors.SynthesisDesignPreview.render(IDS[preview],(float)(clock.sample(System.nanoTime(),mc.isGamePaused())*1.5));
        }finally{GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);RenderHelper.enableGUIStandardItemLighting();}
        }
        for(int i=0;i<7;i++)slot(layout.machineX(i),machineY());
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(layout.inventoryX+col*18,inventoryY()+row*18);
        for(int col=0;col<9;col++)slot(layout.inventoryX+col*18,hotbarY());
        drawRect(guiLeft+layout.progressX(),guiTop+machineY()+4,guiLeft+layout.progressX()+layout.progressWidth(),guiTop+machineY()+12,0xFF303947);
        int progress=Math.max(0,Math.min(200,container.values[1]));
        drawRect(guiLeft+layout.progressX(),guiTop+machineY()+4,guiLeft+layout.progressX()+layout.progressWidth()*progress/200,guiTop+machineY()+12,0xFF55CBA4);
    }
    private void slot(int x,int y){drawRect(guiLeft+x-1,guiTop+y-1,guiLeft+x+17,guiTop+y+17,0xFF67727E);drawRect(guiLeft+x,guiTop+y,guiLeft+x+16,guiTop+y+16,0xFF0B1017);}
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){
        fontRenderer.drawString("Synthesis Tower",12,10,0xFFFFFF);
        if(page==0){if(!compact){fontRenderer.drawString("Designs",12,60,0xD4DFE9);fontRenderer.drawString("Finished structure",174,60,0xD4DFE9);}
        }
        if(page==1&&!compact){fontRenderer.drawString("Physical Redstone or any receiving channel.",12,142,0xA7B3C0);fontRenderer.drawString("A disabled tower keeps its current build.",12,155,0xA7B3C0);}
        fontRenderer.drawString("Matter",layout.machineX(0)-6,machineY()-12,0xC3CFDB);fontRenderer.drawString("Core",layout.machineX(1)-4,machineY()-12,0xC3CFDB);
        fontRenderer.drawString("Chip",layout.machineX(3)-4,machineY()-12,0xC3CFDB);
        fontRenderer.drawString(Integer.toString(container.values[2]),12,compact?210:272,0xA7B3C0);
        // Inventory is offset right to leave power and production status visible.
        fontRenderer.drawString("Inventory",layout.inventoryX,inventoryY()-10,0xD4DFE9);
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(page==1){receiver.mouseClicked(x,y,button);if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,2))return;}
        if(page==0&&(button==0||button==1)&&x>=guiLeft+12&&x<guiLeft+layout.previewLeft-10&&y>=previewTop()+(compact?0:4)&&y<previewTop()+(compact?0:4)+3*(compact?17:24)){
            int choice=(y-previewTop()-(compact?0:4))/(compact?17:24);
            PacketHandler.INSTANCE.sendToServer(new MessageSynthesisTower(inventorySlots.windowId,container.tile.getPos(),choice));return;
        }
        super.mouseClicked(x,y,button);
    }
}
