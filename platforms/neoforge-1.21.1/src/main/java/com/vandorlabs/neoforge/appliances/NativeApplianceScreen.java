package com.vandorlabs.neoforge.appliances;

import com.vandorlabs.appliance.ApplianceAutomation;
import com.vandorlabs.neoforge.client.NativeDialogTabButton;
import com.vandorlabs.neoforge.client.NativeDialogVisuals;
import com.vandorlabs.neoforge.mixin.NativeSlotPositionAccess;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.DialogStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NativeApplianceScreen extends AbstractContainerScreen<NativeApplianceMenu> {
    private final int[] slotX,slotY;
    private boolean redstone;
    private int activation;
    private Button mode;
    private EditBox channels;
    private String draft;

    public NativeApplianceScreen(NativeApplianceMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);
        imageWidth=176;
        imageHeight=190;
        inventoryLabelY=96;
        activation=menu.tile.activation();
        draft=menu.tile.getRedstoneChannels().toString();
        slotX=new int[menu.slots.size()];
        slotY=new int[menu.slots.size()];
        for(int i=0;i<slotX.length;i++){
            slotX[i]=menu.slots.get(i).x;
            slotY[i]=menu.slots.get(i).y+24;
        }
    }

    @Override protected void init(){super.init();rebuild();}

    private void tab(String name,int x,boolean target){
        var tab=new NativeDialogTabButton(leftPos+x,topPos+24,74,18,Component.literal(name),button->{
            if(saveSettings()){redstone=target;rebuild();}
        });
        tab.active=redstone!=target;
        addRenderableWidget(tab);
    }

    private void rebuild(){
        clearWidgets();
        channels=null;
        mode=null;
        tab("Inventory",8,false);
        tab("Redstone",94,true);
        for(int i=0;i<slotX.length;i++){
            var slot=(NativeSlotPositionAccess)(Object)menu.slots.get(i);
            slot.vandorlabs$slotX(redstone?-10000:slotX[i]);
            slot.vandorlabs$slotY(redstone?-10000:slotY[i]);
        }
        if(redstone){
            mode=addRenderableWidget(Button.builder(modeLabel(),button->cycle(1)).bounds(leftPos+8,topPos+96,160,20).build());
            channels=addRenderableWidget(new EditBox(font,leftPos+8,topPos+68,160,18,Component.literal("Receive channels")));
            channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);
            channels.setValue(draft);
            channels.setResponder(value->draft=value);
        }else{
            addRenderableWidget(Button.builder(Component.literal(menu.tile.scanner()?"Scan":"Print"),
                    button->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+8,topPos+59,44,18).build());
        }
    }

    private Component modeLabel(){return Component.literal("Trigger: "+ApplianceAutomation.label(activation));}
    private void cycle(int step){activation=Math.floorMod(activation+step,3);mode.setMessage(modeLabel());saveSettings();}
    private boolean saveSettings(){
        if(channels==null)return true;
        var bank=ChannelList.parse(draft);
        channels.setTextColor(bank==null?0xff7777:0xe0e0e0);
        if(bank==null)return false;
        PacketDistributor.sendToServer(new NativeApplianceRedstonePayload(menu.containerId,menu.tile.getBlockPos(),activation,bank));
        return true;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==1&&mode!=null&&mode.isMouseOver(x,y)){cycle(-1);return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public void onClose(){if(saveSettings())super.onClose();}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        NativeDialogVisuals.panel(g,font,title,leftPos,topPos,imageWidth,imageHeight,DialogStyle.PORT);
        if(redstone){
            g.drawString(font,"Receive channels (0 = local)",leftPos+8,topPos+53,0xbbbbbb,false);
            g.drawString(font,"Runs once per signal edge.",leftPos+8,topPos+126,0xbbbbbb,false);
            return;
        }
        for(var slot:menu.slots){
            g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xff61727b);
            g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xff111a22);
        }
        if(menu.tile.scanner())g.drawCenteredString(font,"Original",leftPos+88,topPos+47,0xbbbbbb);
        else{
            g.drawCenteredString(font,"Ink",leftPos+70,topPos+47,0xbbbbbb);
            g.drawCenteredString(font,"Paper",leftPos+106,topPos+47,0xbbbbbb);
        }
        g.drawCenteredString(font,"Output",leftPos+142,topPos+47,0xbbbbbb);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){if(!redstone)g.drawString(font,playerInventoryTitle,8,inventoryLabelY,0xbbbbbb,false);}
    @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);}
    @Override public boolean isPauseScreen(){return false;}
}
