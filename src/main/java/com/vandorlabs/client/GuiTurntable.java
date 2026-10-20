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
    private int scanMode,rotationMode;
    private boolean clockwise,observedClockwise;
    private net.minecraftforge.fml.client.config.GuiSlider heightSlider,angleSlider,rpmSlider,autoSnapSlider;
    private int scanHeight,requestedAngle=-1,pendingTicks;
    private boolean syncingAngle;
    private double speed,snapTolerance;
    private String error="";
    public GuiTurntable(ContainerTurntable container){super(container);tile=container.tile;scanMode=tile.scanMode;rotationMode=tile.rotationMode;clockwise=observedClockwise=tile.clockwise;scanHeight=tile.height;speed=tile.rpm;snapTolerance=tile.autoSnapTolerance;}
    @Override public void initGui(){xSize=Math.min(400,width-12);ySize=Math.min(252,height-12);super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);buttonList.clear();int x=guiLeft+12,y=guiTop;
        heightSlider=new net.minecraftforge.fml.client.config.GuiSlider(10,x,y+24,130,20,"Height: ","",1,16,scanHeight,false,true,slider->{int h=slider.getValueInt();slider.setValue(h);if(h!=scanHeight){scanHeight=h;apply();}});heightSlider.enabled=tile.captureId==null;buttonList.add(heightSlider);
        rpmSlider=new net.minecraftforge.fml.client.config.GuiSlider(12,x+134,y+24,xSize-158,20,"RPM: ","",1,20,Math.round(speed*2),false,true,slider->{double next=slider.getValueInt()/2D;slider.displayString=String.format(java.util.Locale.ROOT,"RPM: %.1f",next);if(speed!=next){speed=next;apply();}});buttonList.add(rpmSlider);
        rpmSlider.displayString=String.format(java.util.Locale.ROOT,"RPM: %.1f",speed);
        field(0,x+140,y+57,xSize-166,tile.scanChannels.toString());field(1,x+140,y+79,xSize-166,tile.rotationChannels.toString());field(2,x+140,y+101,xSize-166,tile.directionChannels.toString());field(3,x+140,y+123,xSize-166,tile.restoreChannels.toString());
        buttonList.add(new GuiButton(2,x,y+56,130,20,""));buttonList.add(new GuiButton(3,x,y+78,130,20,""));
        buttonList.add(new GuiButton(4,x,y+144,130,20,""));
        angleSlider=new net.minecraftforge.fml.client.config.GuiSlider(11,x+134,y+144,xSize-158,20,"Angle: "," degrees",0,360,tile.angle(1),false,true,slider->{if(syncingAngle)return;int heading=slider.getValueInt();slider.setValue(heading);if(heading==requestedAngle&&pendingTicks>0)return;requestedAngle=heading;pendingTicks=40;NBTTagCompound angle=new NBTTagCompound();angle.setDouble("Angle",heading);send(4,angle);});
        angleSlider.enabled=tile.display()!=null&&!tile.running();buttonList.add(angleSlider);
        autoSnapSlider=new net.minecraftforge.fml.client.config.GuiSlider(13,x,y+166,xSize-24,20,"Auto-snap: "," degrees",0,50,Math.round(snapTolerance*10),false,true,slider->{double next=slider.getValueInt()/10D;slider.displayString=String.format(java.util.Locale.ROOT,"Auto-snap: %.1f degrees",next);if(snapTolerance!=next){snapTolerance=next;apply();}});buttonList.add(autoSnapSlider);
        autoSnapSlider.displayString=String.format(java.util.Locale.ROOT,"Auto-snap: %.1f degrees",snapTolerance);
        int w=(xSize-30)/3;for(int i=0;i<3;i++)buttonList.add(new GuiButton(7+i,x+i*(w+2),y+ySize-28,w,20,new String[]{"Scan","Restore","Done"}[i]));labels();
    }
    private void field(int id,int x,int y,int w,String text){GuiTextField field=new GuiTextField(id,fontRenderer,x,y,w,18);field.setMaxStringLength(ChannelList.MAX_TEXT_LENGTH);field.setText(text);fields[id]=field;}
    private void labels(){for(GuiButton b:buttonList)switch(b.id){case 2:b.displayString="Scan: "+new String[]{"Disabled","ON","OFF"}[scanMode];break;case 3:b.displayString="Rotate: "+new String[]{"","ON","OFF"}[rotationMode];break;case 4:b.displayString=clockwise?"Clockwise":"Counterclockwise";break;default:break;}}
    private NBTTagCompound values(){
        for(GuiTextField field:fields)if(field==null)return null;
        for(GuiTextField field:fields)if(ChannelList.parse(field.getText())==null){error="Use comma-separated channel numbers";return null;}
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Height",scanHeight);tag.setDouble("RPM",speed);tag.setDouble("AutoSnapTolerance",snapTolerance);tag.setInteger("Axis",0);tag.setBoolean("Reverse",false);tag.setBoolean("Clockwise",clockwise);tag.setInteger("ScanMode",scanMode);tag.setInteger("RotationMode",rotationMode);tag.setString("ScanChannels",fields[0].getText());tag.setString("RotationChannels",fields[1].getText());tag.setString("DirectionChannels",fields[2].getText());tag.setString("RestoreChannels",fields[3].getText());return tag;
    }
    private void send(int operation,NBTTagCompound values){PacketHandler.INSTANCE.sendToServer(new MessageTurntableSettings(inventorySlots.windowId,operation,values));}
    private void apply(){if(fields[3]==null)return;NBTTagCompound tag=values();if(tag!=null){error="";send(0,tag);}}
    @Override protected void actionPerformed(GuiButton button){switch(button.id){case 2:scanMode=GuiOptionCycle.next(scanMode,3);break;case 3:rotationMode=rotationMode==1?2:1;break;case 4:clockwise=!clockwise;break;case 7:NBTTagCompound tag=values();if(tag!=null){error="";send(3,tag);}return;case 8:send(2,new NBTTagCompound());return;case 9:mc.player.closeScreen();return;case 10:case 11:case 12:case 13:return;default:return;}apply();labels();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xee18212c);}
    private String statusText(){return !error.isEmpty()?error:!tile.lastError.isEmpty()?tile.lastError:tile.message;}
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){
        fontRenderer.drawString("Turntable Controller",12,8,0xffffff);fontRenderer.drawString("Channels: 0 = local / pulses off",12,46,0xcdd7e4);fontRenderer.drawString("Direction pulse",12,104,0xcdd7e4);fontRenderer.drawString("Restore pulse",12,126,0xcdd7e4);
        drawRect(10,188,xSize-10,ySize-30,0xff101722);
        java.util.List<String> lines=fontRenderer.listFormattedStringToWidth("Status: "+statusText(),xSize-28);
        int count=Math.min(lines.size(),Math.max(1,(ySize-31-189)/fontRenderer.FONT_HEIGHT));
        for(int i=0;i<count;i++)fontRenderer.drawString(lines.get(i),14,189+i*fontRenderer.FONT_HEIGHT,error.isEmpty()&&tile.lastError.isEmpty()?0xcdd7e4:0xff8888);
    }
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);for(GuiTextField field:fields)field.drawTextBox();renderHoveredToolTip(x,y);for(GuiButton b:buttonList)if(b.id==8&&x>=b.x&&x<b.x+b.width&&y>=b.y&&y<b.y+b.height)drawHoveringText(java.util.Collections.singletonList("Restore at "+Math.floorMod((int)Math.round(tile.angle(1)/90)*90,360)+" degrees (nearest block heading)"),x,y);if(x>=guiLeft+10&&x<guiLeft+xSize-10&&y>=guiTop+188&&y<guiTop+ySize-30)drawHoveringText(fontRenderer.listFormattedStringToWidth(statusText(),Math.min(320,width-30)),x,y);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,2,3,4))return;super.mouseClicked(x,y,button);for(GuiTextField field:fields)field.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char c,int key)throws IOException{for(GuiTextField field:fields)if(field.textboxKeyTyped(c,key)){apply();return;}super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();if(tile.clockwise!=observedClockwise){clockwise=observedClockwise=tile.clockwise;}heightSlider.enabled=tile.captureId==null;angleSlider.enabled=tile.display()!=null&&!tile.running();
        if(pendingTicks>0){pendingTicks--;double difference=Math.abs(((tile.angle(1)-requestedAngle+540)%360)-180);if(difference<.6||tile.running()||!tile.lastError.isEmpty())pendingTicks=0;}
        if(!angleSlider.dragging&&pendingTicks==0){syncingAngle=true;try{angleSlider.setValue(tile.angle(1));angleSlider.updateSlider();}finally{syncingAngle=false;}}labels();for(GuiTextField field:fields)field.updateCursorCounter();}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
