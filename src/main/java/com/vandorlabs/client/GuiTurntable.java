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
    private final GuiTextField[] fields=new GuiTextField[3];
    private int scanMode,rotationMode,axis;
    private boolean clockwise;
    private net.minecraftforge.fml.client.config.GuiSlider heightSlider,angleSlider;
    private int scanHeight;
    private String error="";
    public GuiTurntable(ContainerTurntable container){super(container);tile=container.tile;scanMode=tile.scanMode;rotationMode=tile.rotationMode;axis=tile.axis;clockwise=tile.clockwise;scanHeight=tile.height;}
    @Override public void initGui(){xSize=Math.min(400,width-12);ySize=Math.min(268,height-12);super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);buttonList.clear();int x=guiLeft+12,y=guiTop;
        heightSlider=new net.minecraftforge.fml.client.config.GuiSlider(10,x,y+28,130,20,"Height: ","",1,16,scanHeight,false,true,slider->{int h=slider.getValueInt();slider.setValue(h);if(h!=scanHeight){scanHeight=h;apply();}});heightSlider.enabled=tile.captureId==null;buttonList.add(heightSlider);field(0,x+172,y+28,xSize-208,Double.toString(tile.rpm));
        field(1,x+140,y+90,xSize-166,tile.scanChannels.toString());field(2,x+140,y+126,xSize-166,tile.rotationChannels.toString());
        buttonList.add(new GuiButton(0,x,y+52,(xSize-28)/2,20,""));
        buttonList.add(new GuiButton(2,x,y+88,130,20,""));buttonList.add(new GuiButton(3,x,y+124,130,20,""));
        buttonList.add(new GuiButton(4,x+(xSize-28)/2+4,y+52,(xSize-28)/2,20,""));
        angleSlider=new net.minecraftforge.fml.client.config.GuiSlider(11,x,y+150,xSize-24,20,"Angle: "," degrees",0,360,tile.angle(1),false,true,slider->{int heading=slider.getValueInt();slider.setValue(heading);NBTTagCompound angle=new NBTTagCompound();angle.setDouble("Angle",heading);send(4,angle);});
        angleSlider.enabled=tile.display()!=null&&!tile.running();buttonList.add(angleSlider);
        int w=(xSize-30)/3;for(int i=0;i<3;i++)buttonList.add(new GuiButton(7+i,x+i*(w+2),y+ySize-28,w,20,new String[]{"Scan","Restore","Done"}[i]));labels();
    }
    private void field(int id,int x,int y,int w,String text){GuiTextField field=new GuiTextField(id,fontRenderer,x,y,w,18);field.setMaxStringLength(id==0?12:ChannelList.MAX_TEXT_LENGTH);field.setText(text);fields[id]=field;}
    private void labels(){for(GuiButton b:buttonList)switch(b.id){case 0:b.enabled=tile.captureId==null;b.displayString="Axis: "+new String[]{"Auto","X","Y","Z"}[axis];break;case 2:b.displayString="Scan: "+new String[]{"Disabled","ON","OFF"}[scanMode];break;case 3:b.displayString="Rotate: "+new String[]{"","ON","OFF"}[rotationMode];break;case 4:b.displayString=clockwise?"Clockwise":"Counterclockwise";break;default:break;}}
    private NBTTagCompound values(){int height=scanHeight;double rpm;try{rpm=Double.parseDouble(fields[0].getText());com.vandorlabs.turntable.TurntableGeometry.degreesPerTick(rpm);}catch(IllegalArgumentException invalid){error="Height 1-16; speed 0.1-30 RPM";return null;}if(height<1||height>16||ChannelList.parse(fields[1].getText())==null||ChannelList.parse(fields[2].getText())==null){error="Use height 1-16 and comma-separated channel numbers";return null;}NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Height",height);tag.setDouble("RPM",rpm);tag.setInteger("Axis",axis);tag.setBoolean("Reverse",false);tag.setBoolean("Clockwise",clockwise);tag.setInteger("ScanMode",scanMode);tag.setInteger("RotationMode",rotationMode);tag.setString("ScanChannels",fields[1].getText());tag.setString("RotationChannels",fields[2].getText());return tag;}
    private void send(int operation,NBTTagCompound values){PacketHandler.INSTANCE.sendToServer(new MessageTurntableSettings(inventorySlots.windowId,operation,values));}
    private void apply(){if(fields[2]==null)return;NBTTagCompound tag=values();if(tag!=null){error="";send(0,tag);}}
    @Override protected void actionPerformed(GuiButton button){switch(button.id){case 0:axis=(axis+1)%4;break;case 2:scanMode=(scanMode+1)%3;break;case 3:rotationMode=rotationMode==1?2:1;break;case 4:clockwise=!clockwise;break;case 7:NBTTagCompound tag=values();if(tag!=null){error="";send(3,tag);}return;case 8:send(2,new NBTTagCompound());return;case 9:mc.player.closeScreen();return;case 10:case 11:return;default:return;}apply();labels();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xee18212c);}
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){fontRenderer.drawString("Turntable Controller",12,12,0xffffff);fontRenderer.drawString("RPM",148,32,0xcdd7e4);fontRenderer.drawString("Scan channels (0 = local redstone)",12,77,0xcdd7e4);fontRenderer.drawString("Rotation channels (0 = local redstone)",12,113,0xcdd7e4);fontRenderer.drawString("Restore: "+Math.floorMod((int)Math.round(tile.angle(1)/90)*90,360)+" degrees (nearest block heading)",12,174,0xcdd7e4);
        java.util.List<String> lines=fontRenderer.listFormattedStringToWidth(error.isEmpty()?tile.message:error,xSize-24);
        int count=Math.min(lines.size(),Math.max(0,(ySize-28-190)/fontRenderer.FONT_HEIGHT));
        for(int i=0;i<count;i++)fontRenderer.drawString(lines.get(i),12,190+i*fontRenderer.FONT_HEIGHT,error.isEmpty()?0xcdd7e4:0xff8888);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);for(GuiTextField field:fields)field.drawTextBox();renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{super.mouseClicked(x,y,button);for(GuiTextField field:fields)field.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char c,int key)throws IOException{for(GuiTextField field:fields)if(field.textboxKeyTyped(c,key)){apply();return;}super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();heightSlider.enabled=tile.captureId==null;angleSlider.enabled=tile.display()!=null&&!tile.running();
        if(!angleSlider.dragging){angleSlider.setValue(tile.angle(1));angleSlider.updateSlider();}labels();for(GuiTextField field:fields)field.updateCursorCounter();}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
