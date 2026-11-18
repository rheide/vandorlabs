package com.vandorlabs.client;

import com.vandorlabs.appliance.ApplianceAutomation;
import com.vandorlabs.container.ContainerChipAppliance;
import com.vandorlabs.network.MessageSynthesisRedstone;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.InfuserDialog;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.lwjgl.input.Keyboard;

/** Standard machine inventory and receiving controls. */
public final class GuiChipAppliance extends GuiContainer {
    private final ContainerChipAppliance container;
    private final int[] slotX,slotY;
    private int page,activation;
    private ChannelList channels;
    private ReceiverPane receiver;
    private GuiButton inventoryTab,redstoneTab,runButton;

    public GuiChipAppliance(ContainerChipAppliance container){
        super(container);
        this.container=container;
        xSize=InfuserDialog.WIDTH;
        ySize=InfuserDialog.HEIGHT;
        activation=container.tile.activation();
        channels=container.tile.getRedstoneChannels();
        int slots=container.inventorySlots.size();
        slotX=new int[slots];slotY=new int[slots];
        for(int i=0;i<slots;i++){Slot slot=container.inventorySlots.get(i);slotX[i]=slot.xPos;slotY[i]=slot.yPos;}
    }

    @Override public void initGui(){
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int tabWidth=(xSize-30)/2;
        inventoryTab=new DialogTabButton(1,guiLeft+12,guiTop+32,tabWidth,20,"Inventory");
        redstoneTab=new DialogTabButton(2,guiLeft+18+tabWidth,guiTop+32,tabWidth,20,"Redstone");
        runButton=new GuiButton(0,guiLeft+12,guiTop+182,72,20,container.tile.scanner()?"Scan":"Print");
        receiver=new ReceiverPane(40,3,fontRenderer,guiLeft+12,guiTop+68,xSize-24,channels,activation);
        updateTriggerLabel();
        buttonList.add(inventoryTab);
        buttonList.add(redstoneTab);
        buttonList.add(runButton);
        buttonList.add(receiver.button());
        updatePage();
    }

    private void updatePage(){
        // GuiScreen iterates the live button list throughout a click.
        boolean inventory=page==0;
        inventoryTab.enabled=!inventory;
        redstoneTab.enabled=inventory;
        runButton.visible=inventory;
        receiver.setVisible(!inventory);
        for(int i=0;i<slotX.length;i++){
            Slot slot=container.inventorySlots.get(i);
            slot.xPos=inventory?slotX[i]:-10000;
            slot.yPos=inventory?slotY[i]:-10000;
        }
    }

    private void updateTriggerLabel(){receiver.button().displayString="Trigger: "+ApplianceAutomation.label(activation);}

    private boolean saveRedstone(){
        if(page!=1||receiver==null)return true;
        ChannelList parsed=receiver.channels();
        if(parsed==null)return false;
        channels=parsed;
        PacketHandler.INSTANCE.sendToServer(new MessageSynthesisRedstone(inventorySlots.windowId,container.tile.getPos(),activation,channels));
        return true;
    }

    @Override protected void actionPerformed(GuiButton button){
        if(button.id==0)mc.playerController.sendEnchantPacket(inventorySlots.windowId,0);
        else if(button.id==3){
            activation=GuiOptionCycle.next(activation,3);
            updateTriggerLabel();
            saveRedstone();
        }else if((button.id==1||button.id==2)&&saveRedstone()){page=button.id-1;updatePage();}
    }

    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(receiver!=null){
            receiver.mouseClicked(x,y,button);
            if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,3))return;
        }
        super.mouseClicked(x,y,button);
    }

    @Override protected void keyTyped(char character,int key)throws IOException{
        if(receiver!=null&&receiver.keyTyped(character,key)){saveRedstone();return;}
        super.keyTyped(character,key);
    }

    @Override public void updateScreen(){super.updateScreen();if(receiver!=null)receiver.field().updateCursorCounter();}
    @Override public void onGuiClosed(){saveRedstone();Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);if(receiver!=null)receiver.draw();renderHoveredToolTip(x,y);}

    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,InfuserDialog.STYLE.body);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+InfuserDialog.STYLE.headerHeight,InfuserDialog.STYLE.header);
        if(page!=0)return;
        for(Slot slot:inventorySlots.inventorySlots){int sx=guiLeft+slot.xPos,sy=guiTop+slot.yPos;drawRect(sx-1,sy-1,sx+17,sy+17,InfuserDialog.FRAME);drawRect(sx,sy,sx+16,sy+16,InfuserDialog.SLOT);}
    }

    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString(container.tile.scanner()?"Scanner":"Printer",12,10,0xffffff);
        DialogHeader.logo(0,0,xSize,InfuserDialog.STYLE.logoHeaderHeight);
        if(page==1){fontRenderer.drawString("Runs once per selected signal edge.",12,140,InfuserDialog.MUTED_COLOR);return;}
        fontRenderer.drawString("Chip",36,60,InfuserDialog.TEXT_COLOR);
        fontRenderer.drawString(container.tile.scanner()?"Original":"Ink sac",104,60,InfuserDialog.TEXT_COLOR);
        if(!container.tile.scanner())fontRenderer.drawString("Paper",176,60,InfuserDialog.TEXT_COLOR);
        fontRenderer.drawString("Output",container.tile.scanner()?175:247,60,InfuserDialog.TEXT_COLOR);
        fontRenderer.drawString("Inventory",100,112,InfuserDialog.TEXT_COLOR);
        fontRenderer.drawString(container.tile.scanner()?"Original documents remain in the top frame.":"One ink sac + one paper per print.",12,210,InfuserDialog.MUTED_COLOR);
    }
}
