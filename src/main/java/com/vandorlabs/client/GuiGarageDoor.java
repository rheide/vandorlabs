package com.vandorlabs.client;

import com.vandorlabs.garage.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.network.PacketHandler;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import java.io.IOException;

/** Same mechanism/redstone tabs, channel field and integer slider as other controllers. */
public final class GuiGarageDoor extends GuiContainer {
    private final GarageDoorTile tile;
    private int page,ticks;private boolean local,opens;private GuiTextField channels;private String draft,error="",last="";
    public GuiGarageDoor(GarageDoorContainer container){super(container);tile=container.tile;ticks=tile.ticksPerSection;local=tile.localRedstone;opens=tile.signalOpens;draft=tile.getRedstoneChannels().toString();xSize=340;ySize=196;}
    @Override public void initGui(){if(channels!=null)draft=channels.getText();super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);buttonList.clear();
        buttonList.add(new DialogTabButton(90,guiLeft+12,guiTop+28,150,18,"Mechanism"));buttonList.add(new DialogTabButton(91,guiLeft+168,guiTop+28,160,18,"Redstone"));
        net.minecraftforge.fml.client.config.GuiSlider speed=new net.minecraftforge.fml.client.config.GuiSlider(5,guiLeft+12,guiTop+61,xSize-24,20,"Ticks per section: ","",5,30,ticks,false,true,s->{int value=s.getValueInt();s.setValue(value);if(value!=ticks){ticks=value;apply();}});speed.visible=page==0;buttonList.add(speed);
        channels=new GuiTextField(6,fontRenderer,guiLeft+112,guiTop+64,xSize-126,18);channels.setMaxStringLength(256);channels.setText(draft);channels.setVisible(page==1);
        GuiButton a=new GuiButton(1,guiLeft+12,guiTop+96,150,20,"Local Redstone: "+(local?"On":"Off")),b=new GuiButton(2,guiLeft+168,guiTop+96,160,20,"Signal: "+(opens?"Open":"Close"));a.visible=b.visible=page==1;buttonList.add(a);buttonList.add(b);
        buttonList.add(new GuiButton(3,guiLeft+12,guiTop+ySize-28,100,20,"Open"));buttonList.add(new GuiButton(4,guiLeft+118,guiTop+ySize-28,100,20,"Close"));buttonList.add(new GuiButton(0,guiLeft+224,guiTop+ySize-28,104,20,"Done"));
        for(GuiButton button:buttonList)if(button.id==90||button.id==91)button.enabled=button.id!=90+page;
    }
    private void apply(){try{ChannelList selected=ChannelList.parse(channels==null?draft:channels.getText());String key=ticks+":"+local+":"+opens+":"+selected;if(!key.equals(last)){PacketHandler.INSTANCE.sendToServer(new GarageDoorMessage(inventorySlots.windowId,0,ticks,local,opens,selected));last=key;}error="";}catch(IllegalArgumentException invalid){error=invalid.getMessage();}}
    @Override protected void actionPerformed(GuiButton button){if(button.id>=90){draft=channels.getText();page=button.id-90;initGui();return;}if(button.id==1||button.id==2){if(button.id==1)local=!local;else opens=!opens;apply();initGui();return;}if(button.id==3||button.id==4){apply();if(error.isEmpty())PacketHandler.INSTANCE.sendToServer(new GarageDoorMessage(inventorySlots.windowId,button.id==3?1:2,ticks,local,opens,ChannelList.parse(channels.getText())));return;}if(button.id==0){apply();if(error.isEmpty())mc.player.closeScreen();}}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xee18212c);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Sectional Garage Door",12,10,0xffffff);if(page==0){fontRenderer.drawString("Height: "+tile.height+" sections",12,90,0xcbd6e2);fontRenderer.drawString(String.format(java.util.Locale.ROOT,"Full travel: %.2f seconds",tile.height*ticks*.05),12,106,0xcbd6e2);fontRenderer.drawString("Keep the overhead track clear.",12,128,0x9fb1c5);}else{fontRenderer.drawString("Channels",12,69,0xcbd6e2);fontRenderer.drawSplitString("Channels and local power operate linked columns. Configure any section with Shift-right-click or the Configurizer.",12,126,xSize-24,0x9fb1c5);}if(!error.isEmpty())fontRenderer.drawString(error,12,ySize-42,0xff7777);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);DialogOverlay.prepare();if(page==1)channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,2))return;super.mouseClicked(x,y,button);if(page==1)channels.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char typed,int key)throws IOException{if(page==1&&channels.textboxKeyTyped(typed,key))return;if(key==1||key==mc.gameSettings.keyBindInventory.getKeyCode())apply();super.keyTyped(typed,key);}
    @Override public void updateScreen(){super.updateScreen();channels.updateCursorCounter();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
}
