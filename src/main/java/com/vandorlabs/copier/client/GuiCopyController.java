package com.vandorlabs.copier.client;

import com.vandorlabs.copier.*;
import com.vandorlabs.client.GuiOptionCycle;
import com.vandorlabs.client.RedstoneActivationButton;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import java.io.IOException;

public final class GuiCopyController extends GuiContainer {
    private final CopyControllerTile tile;
    private GuiTextField increment,channels;
    private net.minecraftforge.fml.client.config.GuiSlider heightSlider;
    private int scanHeight;
    private int mode;
    private String error="";
    public GuiCopyController(CopyControllerContainer container){super(container);tile=container.tile;mode=tile.triggerMode;scanHeight=tile.height;xSize=340;ySize=228;}
    @Override public void initGui(){
        xSize=Math.min(400,width-12);super.initGui();buttonList.clear();
        increment=new GuiTextField(1,fontRenderer,guiLeft+12,guiTop+78,xSize-24,18);increment.setMaxStringLength(com.vandorlabs.redstone.ChannelRange.INCREMENT_TEXT_LENGTH);increment.setText(Integer.toString(tile.channelIncrement));
        channels=new GuiTextField(2,fontRenderer,guiLeft+12,guiTop+144,xSize-24,18);channels.setMaxStringLength(ChannelList.MAX_TEXT_LENGTH);channels.setText(tile.channels.toString());
        buttonList.add(new RedstoneActivationButton(0,guiLeft+12,guiTop+104,xSize-24,mode));
        int w=(xSize-28)/3;for(int i=0;i<3;i++)buttonList.add(new GuiButton(i+1,guiLeft+12+i*(w+2),guiTop+202,w,20,new String[]{"Copy","Check areas","Done"}[i]));
        heightSlider=new net.minecraftforge.fml.client.config.GuiSlider(10,guiLeft+12,guiTop+22,xSize-24,20,"Height: ","",1,16,scanHeight,false,true,slider->{int next=slider.getValueInt();slider.setValue(next);if(next!=scanHeight){scanHeight=next;submit(0);}});buttonList.add(heightSlider);
        buttonList.add(new GuiButton(11,guiLeft+12,guiTop+46,xSize-24,18,""));
        labels();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    private boolean hasChipMount(){return com.vandorlabs.vehicle.VehicleMounts.find(tile.getWorld(),tile.getPos(),true)!=null;}
    private void labels(){((RedstoneActivationButton)buttonList.get(0)).setMode(mode);for(GuiButton button:buttonList){if(button.id!=3)button.enabled=!tile.busy();if(button.id==11){button.displayString="Source: "+(tile.useChip?(hasChipMount()?"Vehicle Chip":"Vehicle Chip (attach Data Chip Mount)"):"Input Surface");button.visible=tile.useChip||hasChipMount();}}increment.setEnabled(!tile.busy());channels.setEnabled(!tile.busy());}
    private void submit(int operation){try{int h=scanHeight,delta=Integer.parseInt(increment.getText());ChannelList list=ChannelList.parse(channels.getText());if(h<1||h>16||!com.vandorlabs.redstone.ChannelRange.validIncrement(delta)||list==null)throw new IllegalArgumentException();error="";PacketHandler.INSTANCE.sendToServer(new CopyMessage(inventorySlots.windowId,operation,h,delta,mode,list));}catch(IllegalArgumentException invalid){error="Channel increment must be -"+com.vandorlabs.redstone.ChannelRange.MAX_INCREMENT+" to "+com.vandorlabs.redstone.ChannelRange.MAX_INCREMENT;}}
    @Override protected void actionPerformed(GuiButton button){if(button.id==10)return;if(button.id==11){submit(3);return;}if(button.id==3){mc.player.closeScreen();return;}if(button.id==0){mode=GuiOptionCycle.next(mode,3);submit(0);labels();}else submit(button.id);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xee18212c);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString("Copy Controller",12,8,0xffffff);
        if(!tile.useChip&&!hasChipMount())fontRenderer.drawSplitString("Attach a Data Chip Mount to copy from a Vehicle Chip.",12,46,xSize-24,0xcdd7e4);

        fontRenderer.drawString("Redstone Channel Increment On Copy",12,65,0xcdd7e4);
        fontRenderer.drawString("Receive channels (blank = local)",12,131,0xcdd7e4);
        drawRect(10,172,xSize-10,196,0xff101722);
        String status=!error.isEmpty()?error:tile.error.isEmpty()?tile.status:tile.error;
        java.util.List<String> lines=fontRenderer.listFormattedStringToWidth("Status: "+status,xSize-28);
        for(int i=0;i<Math.min(2,lines.size());i++)fontRenderer.drawString(lines.get(i),14,175+i*9,error.isEmpty()&&tile.error.isEmpty()?0xcdd7e4:0xff8888);
    }
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();increment.drawTextBox();channels.drawTextBox();renderHoveredToolTip(x,y);if(x>=guiLeft+10&&x<guiLeft+xSize-10&&y>=guiTop+172&&y<guiTop+196){String status=!error.isEmpty()?error:tile.error.isEmpty()?tile.status:tile.error;java.util.List<String> lines=fontRenderer.listFormattedStringToWidth(status,Math.min(340,width-30));if(lines.size()>2)drawHoveringText(lines,x,y,fontRenderer);}}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0))return;super.mouseClicked(x,y,button);increment.mouseClicked(x,y,button);channels.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char c,int key)throws IOException{if(increment.textboxKeyTyped(c,key)||channels.textboxKeyTyped(c,key))submit(0);else super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();increment.updateCursorCounter();channels.updateCursorCounter();labels();}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
