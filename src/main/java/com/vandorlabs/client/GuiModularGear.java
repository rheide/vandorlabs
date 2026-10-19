package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModularGear;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import java.io.IOException;

public final class GuiModularGear extends GuiContainer {
    private final GearTileEntity tile;private int trigger,direction;private boolean deployed;private GuiTextField channels;private String error="";
    public GuiModularGear(ContainerModularGear container){super(container);tile=container.tile;trigger=tile.triggerMode;direction=tile.direction().ordinal();deployed=tile.target();xSize=300;ySize=204;}
    @Override public void initGui(){xSize=Math.min(340,width-12);super.initGui();buttonList.clear();channels=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+76,xSize-24,18);channels.setMaxStringLength(com.vandorlabs.redstone.ChannelList.MAX_TEXT_LENGTH);channels.setText(tile.getRedstoneChannels().toString());buttonList.add(new GuiButton(0,guiLeft+12,guiTop+30,xSize-24,20,""));buttonList.add(new GuiButton(1,guiLeft+12,guiTop+102,xSize-24,20,""));buttonList.add(new GuiButton(2,guiLeft+12,guiTop+128,xSize-24,20,""));buttonList.add(new GuiButton(4,guiLeft+12,guiTop+176,xSize-24,20,"Done"));labels();org.lwjgl.input.Keyboard.enableRepeatEvents(true);}
    private void labels(){if(tile.isFixedMount()){buttonList.get(0).displayString="Fixed Mount";buttonList.get(0).enabled=false;buttonList.get(1).displayString="No folding";buttonList.get(1).enabled=false;buttonList.get(2).displayString="Always Deployed";buttonList.get(2).enabled=false;channels.setEnabled(false);return;}buttonList.get(0).displayString="Input: "+new String[]{"Manual","ON deploys","OFF deploys"}[trigger];buttonList.get(1).displayString="Fold toward: "+new String[]{"North","East","South","West"}[direction];buttonList.get(1).enabled=tile.isSwingArmController()&&tile.fullyDeployed()&&deployed;buttonList.get(2).displayString=deployed?"Target: Deployed":"Target: Stowed";buttonList.get(2).enabled=trigger==0;}
    private void submit(){if(com.vandorlabs.redstone.ChannelList.parse(channels.getText())==null){error="Invalid channel list";return;}error="";NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Trigger",trigger);tag.setInteger("Direction",direction);tag.setBoolean("Deployed",deployed);tag.setString("Channels",channels.getText());PacketHandler.INSTANCE.sendToServer(new MessageModularGear(inventorySlots.windowId,tag));}
    @Override protected void actionPerformed(GuiButton button){if(button.id==4){mc.player.closeScreen();return;}if(button.id==0)trigger=(trigger+1)%3;else if(button.id==1)direction=(direction+1)%4;else if(button.id==2)deployed=!deployed;else return;submit();labels();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Modular Landing Gear",12,12,0xffffff);fontRenderer.drawString(tile.usesLegacyInput()?"Channels (any, plus local power)":"Channels (0 = local redstone)",12,62,0xcdd7e4);fontRenderer.drawString(error.isEmpty()?tile.columns().size()+" struts / "+(tile.assembly()==null?0:tile.assembly().feet().size())+" feet":error,12,158,error.isEmpty()?0xcdd7e4:0xff8888);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{super.mouseClicked(x,y,button);channels.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char c,int key)throws IOException{if(channels.textboxKeyTyped(c,key))submit();else super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();channels.updateCursorCounter();labels();}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
