package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTurntable;
import com.vandorlabs.network.*;
import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.turntable.TurntableTextures;
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
    private String surfaceOn,surfaceOff;
    private int textureState;
    private HousingTextureList textures;
    public GuiTurntable(ContainerTurntable container){super(container);tile=container.tile;scanMode=tile.scanMode;rotationMode=tile.rotationMode;clockwise=observedClockwise=tile.clockwise;scanHeight=tile.height;speed=tile.rpm;snapTolerance=tile.autoSnapTolerance;surfaceOn=tile.surfaceOn;surfaceOff=tile.surfaceOff;}
    @Override public void initGui(){xSize=Math.min(400,width-12);ySize=Math.min(252,height-12);super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);buttonList.clear();int x=guiLeft+12,y=guiTop;
        textures=null;
        if(textureState!=0){
            textureButtons(x,y+22);
            java.util.List<HousingTextureList.Option> options=new java.util.ArrayList<>();
            for(int i=0;i<TurntableTextures.IDS.length;i++)options.add(new HousingTextureList.Option(i,TurntableTextures.LABELS[i],"Turntable",TurntableTextures.texture(TurntableTextures.IDS[i],true)));
            textures=new HousingTextureList(x,y+50,xSize-31,TurntableTextures.index(textureState==1?surfaceOn:surfaceOff),(ySize-84)/12,options);
            buttonList.add(new GuiButton(16,x,y+ySize-28,xSize-24,20,"Back to controls"));
            return;
        }
        heightSlider=new net.minecraftforge.fml.client.config.GuiSlider(10,x,y+22,130,20,"Height: ","",1,16,scanHeight,false,true,slider->{int h=slider.getValueInt();slider.setValue(h);if(h!=scanHeight){scanHeight=h;apply();}});heightSlider.enabled=tile.captureId==null;buttonList.add(heightSlider);
        rpmSlider=new net.minecraftforge.fml.client.config.GuiSlider(12,x+134,y+22,xSize-158,20,"RPM: ","",1,20,Math.round(speed*2),false,true,slider->{double next=slider.getValueInt()/2D;slider.displayString=String.format(java.util.Locale.ROOT,"RPM: %.1f",next);if(speed!=next){speed=next;apply();}});buttonList.add(rpmSlider);
        rpmSlider.displayString=String.format(java.util.Locale.ROOT,"RPM: %.1f",speed);
        field(0,x+140,y+47,xSize-166,channelText(0,tile.scanChannels.toString()));field(1,x+140,y+67,xSize-166,channelText(1,tile.rotationChannels.toString()));field(2,x+140,y+87,xSize-166,channelText(2,tile.directionChannels.toString()));field(3,x+140,y+107,xSize-166,channelText(3,tile.restoreChannels.toString()));
        buttonList.add(new GuiButton(2,x,y+46,130,20,""));buttonList.add(new GuiButton(3,x,y+66,130,20,""));
        buttonList.add(new GuiButton(4,x,y+128,130,20,""));
        angleSlider=new net.minecraftforge.fml.client.config.GuiSlider(11,x+134,y+128,xSize-158,20,"Angle: "," degrees",0,360,tile.angle(1),false,true,slider->{if(syncingAngle)return;int heading=slider.getValueInt();slider.setValue(heading);if(heading==requestedAngle&&pendingTicks>0)return;requestedAngle=heading;pendingTicks=40;NBTTagCompound angle=new NBTTagCompound();angle.setDouble("Angle",heading);send(4,angle);});
        angleSlider.enabled=tile.display()!=null&&!tile.running();buttonList.add(angleSlider);
        autoSnapSlider=new net.minecraftforge.fml.client.config.GuiSlider(13,x,y+150,xSize-24,20,"Auto-snap: "," degrees",0,50,Math.round(snapTolerance*10),false,true,slider->{double next=slider.getValueInt()/10D;slider.displayString=String.format(java.util.Locale.ROOT,"Auto-snap: %.1f degrees",next);if(snapTolerance!=next){snapTolerance=next;apply();}});buttonList.add(autoSnapSlider);
        autoSnapSlider.displayString=String.format(java.util.Locale.ROOT,"Auto-snap: %.1f degrees",snapTolerance);
        textureButtons(x,y+172);
        int w=(xSize-30)/3;for(int i=0;i<3;i++)buttonList.add(new GuiButton(7+i,x+i*(w+2),y+ySize-26,w,20,new String[]{"Scan","Restore","Done"}[i]));labels();
    }
    private String channelText(int i,String fallback){return fields[i]==null?fallback:fields[i].getText();}
    private void textureButtons(int x,int y){int w=(xSize-28)/2;for(int i=0;i<2;i++){String label=(i==0?"On: ":"Off: ")+TurntableTextures.label(i==0?surfaceOn:surfaceOff);GuiButton button=new GuiButton(14+i,x+i*(w+4),y,w,20,fontRenderer.trimStringToWidth(label,w-10));button.enabled=textureState!=i+1;buttonList.add(button);}}
    void chooseTexture(int choice){if(choice<0||choice>=TurntableTextures.IDS.length)return;if(textureState==1)surfaceOn=TurntableTextures.IDS[choice];else surfaceOff=TurntableTextures.IDS[choice];apply();initGui();}
    private void field(int id,int x,int y,int w,String text){GuiTextField field=new GuiTextField(id,fontRenderer,x,y,w,18);field.setMaxStringLength(ChannelList.MAX_TEXT_LENGTH);field.setText(text);fields[id]=field;}
    private void labels(){for(GuiButton b:buttonList)switch(b.id){case 2:b.displayString="Scan: "+new String[]{"Disabled","ON","OFF"}[scanMode];break;case 3:b.displayString="Rotate: "+new String[]{"","ON","OFF"}[rotationMode];break;case 4:b.displayString=clockwise?"Clockwise":"Counterclockwise";break;default:break;}}
    private NBTTagCompound values(){
        for(GuiTextField field:fields)if(field==null)return null;
        for(GuiTextField field:fields)if(ChannelList.parse(field.getText())==null){error="Use comma-separated channel numbers";return null;}
        NBTTagCompound tag=new NBTTagCompound();tag.setString("SurfaceOn",surfaceOn);tag.setString("SurfaceOff",surfaceOff);tag.setInteger("Height",scanHeight);tag.setDouble("RPM",speed);tag.setDouble("AutoSnapTolerance",snapTolerance);tag.setInteger("Axis",0);tag.setBoolean("Reverse",false);tag.setBoolean("Clockwise",clockwise);tag.setInteger("ScanMode",scanMode);tag.setInteger("RotationMode",rotationMode);tag.setString("ScanChannels",fields[0].getText());tag.setString("RotationChannels",fields[1].getText());tag.setString("DirectionChannels",fields[2].getText());tag.setString("RestoreChannels",fields[3].getText());return tag;
    }
    private void send(int operation,NBTTagCompound values){PacketHandler.INSTANCE.sendToServer(new MessageTurntableSettings(inventorySlots.windowId,operation,values));}
    private void apply(){if(fields[3]==null)return;NBTTagCompound tag=values();if(tag!=null){error="";send(0,tag);}}
    @Override protected void actionPerformed(GuiButton button){switch(button.id){case 14:case 15:textureState=button.id-13;initGui();return;case 16:textureState=0;initGui();return;case 2:scanMode=GuiOptionCycle.next(scanMode,3);break;case 3:rotationMode=rotationMode==1?2:1;break;case 4:clockwise=!clockwise;break;case 7:NBTTagCompound tag=values();if(tag!=null){error="";send(3,tag);}return;case 8:send(2,new NBTTagCompound());return;case 9:mc.player.closeScreen();return;case 10:case 11:case 12:case 13:return;default:return;}apply();labels();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xee18212c);}
    private String statusText(){return !error.isEmpty()?error:!tile.lastError.isEmpty()?tile.lastError:tile.message;}
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){
        fontRenderer.drawString(textureState==0?"Turntable Controller":"Turntable surface textures",12,8,0xffffff);if(textureState!=0)return;fontRenderer.drawString("Direction pulse",12,90,0xcdd7e4);fontRenderer.drawString("Restore pulse",12,110,0xcdd7e4);
        drawRect(10,193,xSize-10,ySize-27,0xff101722);
        java.util.List<String> lines=fontRenderer.listFormattedStringToWidth("Status: "+statusText(),xSize-28);
        int count=Math.min(lines.size(),Math.max(1,(ySize-27-193)/fontRenderer.FONT_HEIGHT));
        for(int i=0;i<count;i++)fontRenderer.drawString(lines.get(i),14,193+i*fontRenderer.FONT_HEIGHT,error.isEmpty()&&tile.lastError.isEmpty()?0xcdd7e4:0xff8888);
    }
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);if(textures!=null){net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();textures.draw(fontRenderer,x,y);net.minecraft.client.renderer.GlStateManager.enableDepth();}else for(GuiTextField field:fields)field.drawTextBox();renderHoveredToolTip(x,y);for(GuiButton b:buttonList)if(b.id==8&&x>=b.x&&x<b.x+b.width&&y>=b.y&&y<b.y+b.height)drawHoveringText(java.util.Collections.singletonList("Restore at "+Math.floorMod((int)Math.round(tile.angle(1)/90)*90,360)+" degrees (nearest block heading)"),x,y);if(x>=guiLeft+10&&x<guiLeft+xSize-10&&textureState==0&&y>=guiTop+193&&y<guiTop+ySize-27)drawHoveringText(fontRenderer.listFormattedStringToWidth(statusText(),Math.min(320,width-30)),x,y);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(textures!=null&&textures.click(x,y,button)){if(textures.picked())chooseTexture(textures.selected());return;}if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,2,3,4))return;super.mouseClicked(x,y,button);if(textureState==0)for(GuiTextField field:fields)field.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char c,int key)throws IOException{if(textureState!=0){if(key==1){textureState=0;initGui();return;}super.keyTyped(c,key);return;}for(GuiTextField field:fields)if(field.textboxKeyTyped(c,key)){apply();return;}super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();if(textureState!=0)return;if(tile.clockwise!=observedClockwise){clockwise=observedClockwise=tile.clockwise;}heightSlider.enabled=tile.captureId==null;angleSlider.enabled=tile.display()!=null&&!tile.running();
        if(pendingTicks>0){pendingTicks--;double difference=Math.abs(((tile.angle(1)-requestedAngle+540)%360)-180);if(difference<.6||tile.running()||!tile.lastError.isEmpty())pendingTicks=0;}
        if(!angleSlider.dragging&&pendingTicks==0){syncingAngle=true;try{angleSlider.setValue(tile.angle(1));angleSlider.updateSlider();}finally{syncingAngle=false;}}labels();for(GuiTextField field:fields)field.updateCursorCounter();}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();if(textures!=null){int x=org.lwjgl.input.Mouse.getEventX()*width/mc.displayWidth,y=height-org.lwjgl.input.Mouse.getEventY()*height/mc.displayHeight-1;textures.wheel(x,y,org.lwjgl.input.Mouse.getEventDWheel());}}
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed){if(textures!=null)textures.drag(y);super.mouseClickMove(x,y,button,elapsed);}
    @Override protected void mouseReleased(int x,int y,int button){if(textures!=null)textures.release();super.mouseReleased(x,y,button);}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
