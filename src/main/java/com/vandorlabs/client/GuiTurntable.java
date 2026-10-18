package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTurntable;
import com.vandorlabs.network.*;
import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import java.io.IOException;

public final class GuiTurntable extends GuiContainer {
    private final TileEntityTurntableController tile;
    private final GuiTextField[] fields=new GuiTextField[4];
    private int scanMode,rotationMode,axis;
    private boolean clockwise,reverse,enabled;
    private String error="";
    public GuiTurntable(ContainerTurntable container){super(container);tile=container.tile;scanMode=tile.scanMode;rotationMode=tile.rotationMode;axis=tile.axis;clockwise=tile.clockwise;reverse=tile.reverse;enabled=tile.enabled;}
    @Override public void initGui(){xSize=Math.min(400,width-12);ySize=Math.min(248,height-12);super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);buttonList.clear();int x=guiLeft+12,y=guiTop;
        field(0,x+60,y+28,48,Integer.toString(tile.height));field(1,x+172,y+28,xSize-208,Double.toString(tile.rpm));
        field(2,x+140,y+90,xSize-166,tile.scanChannels.toString());field(3,x+140,y+126,xSize-166,tile.rotationChannels.toString());
        buttonList.add(new GuiButton(0,x,y+52,(xSize-28)/2,20,""));buttonList.add(new GuiButton(1,x+(xSize-24)/2,y+52,(xSize-28)/2,20,""));
        buttonList.add(new GuiButton(2,x,y+88,130,20,""));buttonList.add(new GuiButton(3,x,y+124,130,20,""));
        buttonList.add(new GuiButton(4,x,y+150,140,20,""));buttonList.add(new GuiButton(5,x+146,y+150,xSize-170,20,""));
        int w=(xSize-32)/4;for(int i=0;i<4;i++)buttonList.add(new GuiButton(6+i,x+i*(w+2),y+ySize-28,w,20,new String[]{"Apply","Scan","Restore","Done"}[i]));labels();
    }
    private void field(int id,int x,int y,int w,String text){GuiTextField field=new GuiTextField(id,fontRenderer,x,y,w,18);field.setMaxStringLength(id<2?12:ChannelList.MAX_TEXT_LENGTH);field.setText(text);fields[id]=field;}
    private void labels(){for(GuiButton b:buttonList)switch(b.id){case 0:b.displayString="Axis: "+new String[]{"Auto","X","Y","Z"}[axis];break;case 1:b.displayString="Capture side: "+(reverse?"Opposite face":"Placed face");break;case 2:b.displayString="Scan: "+new String[]{"Disabled","ON","OFF"}[scanMode];break;case 3:b.displayString="Rotate: "+new String[]{"Manual","ON","OFF"}[rotationMode];break;case 4:b.displayString=clockwise?"Clockwise":"Counterclockwise";break;case 5:b.displayString="Rotation: "+(enabled?"Enabled":"Stopped");break;default:break;}}
    private NBTTagCompound values(){int height;double rpm;try{height=Integer.parseInt(fields[0].getText());rpm=Double.parseDouble(fields[1].getText());com.vandorlabs.turntable.TurntableGeometry.degreesPerTick(rpm);}catch(IllegalArgumentException invalid){error="Height 1-16; speed 0.1-30 RPM";return null;}if(height<1||height>16||ChannelList.parse(fields[2].getText())==null||ChannelList.parse(fields[3].getText())==null){error="Use height 1-16 and comma-separated channel numbers";return null;}NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Height",height);tag.setDouble("RPM",rpm);tag.setInteger("Axis",axis);tag.setBoolean("Reverse",reverse);tag.setBoolean("Clockwise",clockwise);tag.setBoolean("Enabled",enabled);tag.setInteger("ScanMode",scanMode);tag.setInteger("RotationMode",rotationMode);tag.setString("ScanChannels",fields[2].getText());tag.setString("RotationChannels",fields[3].getText());return tag;}
    private void send(int operation,NBTTagCompound values){PacketHandler.INSTANCE.sendToServer(new MessageTurntableSettings(inventorySlots.windowId,operation,values));}
    @Override protected void actionPerformed(GuiButton button){switch(button.id){case 0:axis=(axis+1)%4;break;case 1:reverse=!reverse;break;case 2:scanMode=(scanMode+1)%3;break;case 3:rotationMode=(rotationMode+1)%3;break;case 4:clockwise=!clockwise;break;case 5:enabled=!enabled;break;case 6:case 7:case 9:NBTTagCompound tag=values();if(tag==null)return;error="";send(button.id==7?3:0,tag);if(button.id==9)mc.player.closeScreen();break;case 8:send(2,new NBTTagCompound());break;default:break;}labels();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xee18212c);}
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){fontRenderer.drawString("Turntable Controller",12,12,0xffffff);fontRenderer.drawString("Height",12,32,0xcdd7e4);fontRenderer.drawString("RPM",148,32,0xcdd7e4);fontRenderer.drawString("Scan channels (0 = local redstone)",12,77,0xcdd7e4);fontRenderer.drawString("Rotation channels (0 = local redstone)",12,113,0xcdd7e4);fontRenderer.drawSplitString(error.isEmpty()?tile.message:error,12,178,xSize-24,error.isEmpty()?0xcdd7e4:0xff8888);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);for(GuiTextField field:fields)field.drawTextBox();renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{super.mouseClicked(x,y,button);for(GuiTextField field:fields)field.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char c,int key)throws IOException{for(GuiTextField field:fields)if(field.textboxKeyTyped(c,key))return;super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();for(GuiTextField field:fields)field.updateCursorCounter();}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
