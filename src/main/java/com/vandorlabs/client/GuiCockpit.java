package com.vandorlabs.client;

import com.vandorlabs.container.ContainerCockpit;
import com.vandorlabs.tiles.TileEntityCockpit;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import java.io.IOException;
import java.util.*;

/** One dialog with a separate page for the seat, every surface and every output. */
public final class GuiCockpit extends GuiMaterialPicker {
    private final ContainerCockpit cockpit;
    private final TileEntityCockpit tile;
    private final List<GuiTextField> fields=new ArrayList<>();
    private HousingTextureList textures;
    @Override protected HousingTextureList materialPicker(){return textures;}
    private int page;
    private boolean seating(){return tile.data().seat!=null;}
    private boolean pilotPage(){return seating()&&page==0;}
    private boolean sidePage(){return page==(seating()?1:0);}
    private int displayIndex(){return page-(seating()?1:0)-1;}
    private int controlIndex(){return displayIndex()-tile.displays.size();}
    private int pages(){return (seating()?1:0)+1+tile.displays.size()+tile.controls.size();}
    public GuiCockpit(ContainerCockpit container){super(container);cockpit=container;tile=container.tile;xSize=580;ySize=280;}
    @Override public void initGui(){
        xSize=Math.min(580,width-12);ySize=Math.min(280,height-12);super.initGui();buttonList.clear();fields.clear();textures=null;org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        buttonList.add(new CockpitButton(0,guiLeft+8,guiTop+6,24,20,"<"));buttonList.add(new CockpitButton(1,guiLeft+xSize-32,guiTop+6,24,20,">"));
        buttonList.add(new CockpitButton(2,guiLeft+xSize-92,guiTop+ySize-26,84,20,"Done"));
        int d=displayIndex(),c=controlIndex(),x=guiLeft+12,y=guiTop+48,w=xSize-24;
        if(pilotPage()){
            buttonList.add(new CockpitButton(10,x,guiTop+30,w/2-2,20,"Taxi on boarding: "+(tile.isTaxiMode()?"On":"Off")));
            buttonList.add(new CockpitButton(11,x+w/2+2,guiTop+30,w/2-2,20,"Trigger: "+new String[]{"Disabled","ON","OFF"}[tile.getRedstoneMode()]));
            text(0,x,guiTop+72,w,tile.getRedstoneChannels().toString());
            for(int i=0;i<3;i++)text(i+1,x,guiTop+102+i*28,w,tile.modeChannels(i).toString());
            for(int i=0;i<3;i++)buttonList.add(new CockpitButton(20+i,x+i*w/3,guiTop+ySize-50,w/3-2,20,new String[]{"Block mode","Taxi mode","Hover mode"}[i]));
        }else if(sidePage()){
            int top=tile.design().equals("chair_siderests")?60:34;
            if(top==60)buttonList.add(new CockpitButton(12,x,guiTop+34,w,20,"Height: +"+(tile.getHeight()*2)+"px"));
            textures=new HousingTextureList(x,guiTop+top,w-8,tile.sideTexture(),8).visibleRows(Math.max(2,(ySize-top-34)/HousingTextureList.ROW_HEIGHT));
            buttonList.add(new CockpitButton(34,x,guiTop+ySize-26,110,20,"Default casing"));
        }else if(d<tile.displays.size()){
            TileEntityCockpit.Display screen=tile.displays.get(d);boolean input="touch".equals(screen.role);int controlW=Math.min(154,(xSize-30)/2),cx=guiLeft+xSize-controlW-12;
            textures=new HousingTextureList(x,guiTop+34,cx-x-10,screen.texture,Math.max(3,(ySize-72)/HousingTextureList.ROW_HEIGHT),null,
                    value->HousingTextureList.generalTexture(value) || ScreenTextureList.artwork(value,input)).visibleRows(Math.max(2,(ySize-68)/HousingTextureList.ROW_HEIGHT)).custom(this::choose)
                    .redstone(()->choose(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.REDSTONE_SURFACE)).vehicle(()->choose(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.VEHICLE_SURFACE));
            buttonList.add(new CockpitButton(30,cx,guiTop+34,controlW,20,"Animation: "+(screen.animated?"On":"Off")));
            buttonList.add(new CockpitButton(31,cx,guiTop+58,controlW,20,"Speed: "+screen.speed));
            text(0,cx,guiTop+94,controlW,Integer.toString(screen.gate));
            buttonList.add(new CockpitButton(32,cx,guiTop+118,controlW,20,"Gate: "+(screen.inverted?"OFF":"ON")));
            buttonList.add(new CockpitButton(33,cx,guiTop+142,controlW,20,"Blank surface"));
        }else{
            TileEntityCockpit.Control control=tile.controls.get(c);
            text(0,x,y+12,w,control.label);text(1,x,y+52,w,control.channels.toString());
            buttonList.add(new CockpitButton(40,x,y+84,w,20,"Control: "+new String[]{"Toggle","Slider (0 / 5 / 10 / 15)","Pulse (1 second)"}[control.kind]));
            buttonList.add(new CockpitButton(41,x,y+110,w,20,"Output: "+control.level+"  (operate)"));
        }
    }
    private void text(int id,int x,int y,int width,String value){GuiTextField field=new GuiTextField(id,fontRenderer,x,y,width,18);field.setMaxStringLength(id==0?128:256);field.setText(value);fields.add(field);}
    private void packet(int op,NBTTagCompound values){com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageCockpit(inventorySlots.windowId,op,values));}
    private void choose(int value){if(sidePage())tile.setSideTexture(value);else tile.displays.get(displayIndex()).texture=value;save();}
    private void save(){
        int d=displayIndex(),c=controlIndex();NBTTagCompound tag;
        if(pilotPage()){if(fields.size()!=4)return;for(GuiTextField f:fields)if(ChannelList.parse(f.getText())==null){f.setTextColor(0xff7777);return;}tag=new NBTTagCompound();tag.setBoolean("Pilot",true);tag.setString("Channels",fields.get(0).getText());tag.setBoolean("Taxi",tile.isTaxiMode());tag.setInteger("Trigger",tile.getRedstoneMode());for(int i=0;i<3;i++)tag.setString("Mode"+i,fields.get(i+1).getText());
        }else if(sidePage()){
            tag=new NBTTagCompound();tag.setInteger("SideTexture",tile.sideTexture());if(tile.design().equals("chair_siderests"))tag.setInteger("Height",tile.getHeight());
        }else if(d<tile.displays.size()){
            TileEntityCockpit.Display screen=tile.displays.get(d);try{int gate=Integer.parseInt(fields.get(0).getText());if(gate<0 || gate>65535)throw new NumberFormatException();screen.gate=gate;}catch(NumberFormatException bad){fields.get(0).setTextColor(0xff7777);return;}
            tag=screen.write();tag.setInteger("Display",d);
        }else{
            if(ChannelList.parse(fields.get(1).getText())==null){fields.get(1).setTextColor(0xff7777);return;}
            TileEntityCockpit.Control control=tile.controls.get(c);tag=new NBTTagCompound();tag.setInteger("Control",c);tag.setString("Label",fields.get(0).getText());tag.setString("Channels",fields.get(1).getText());tag.setInteger("Kind",control.kind);
        }
        for(GuiTextField f:fields)f.setTextColor(0xe0e0e0);packet(0,tag);
    }
    @Override protected void actionPerformed(GuiButton button){
        if(button.id==2){save();mc.player.closeScreen();return;}
        if(button.id==0 || button.id==1){save();page=Math.floorMod(page+(button.id==0?-1:1)*GuiOptionCycle.direction(),pages());initGui();return;}
        int d=displayIndex(),c=controlIndex();
        if(button.id==10)tile.setTaxiMode(!tile.isTaxiMode());
        else if(button.id==11)tile.configure(GuiOptionCycle.next(tile.getRedstoneMode(),3),tile.getHeight(),mc.world);
        else if(button.id==12)tile.setHeight(GuiOptionCycle.next(tile.getHeight(),4));
        else if(button.id>=20 && button.id<=22){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Mode",button.id-20);packet(2,tag);return;}
        else if(button.id==30)tile.displays.get(d).animated=!tile.displays.get(d).animated;
        else if(button.id==31)tile.displays.get(d).speed=GuiOptionCycle.next(tile.displays.get(d).speed,1,16);
        else if(button.id==32)tile.displays.get(d).inverted=!tile.displays.get(d).inverted;
        else if(button.id==33)tile.displays.get(d).texture=-1;
        else if(button.id==34)tile.setSideTexture(-1);
        else if(button.id==40)tile.controls.get(c).kind=GuiOptionCycle.next(tile.controls.get(c).kind,3);
        else if(button.id==41){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Control",c);tag.setDouble("Column",((tile.controls.get(c).level/5+1)%4)/3D);packet(1,tag);return;}
        save();initGui();
    }
    @Override protected void keyTyped(char ch,int key)throws IOException{boolean consumed=false;for(GuiTextField f:fields)consumed|=f.textboxKeyTyped(ch,key);if(consumed)save();else super.keyTyped(ch,key);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0,1,10,11,12,30,31,32,40))return;for(GuiTextField f:fields)f.mouseClicked(x,y,button);if(textures!=null && textures.click(x,y,button)){if(textures.picked())choose(textures.selected());return;}super.mouseClicked(x,y,button);}
    @Override public void updateScreen(){for(GuiTextField f:fields)f.updateCursorCounter();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xff304858);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);int d=displayIndex(),c=controlIndex();String title=pilotPage()?"Pilot / vehicle modes":sidePage()?"Sidewalls and armrests":d<tile.displays.size()?tile.displays.get(d).id:tile.controls.get(c).id;drawCenteredString(fontRenderer,title,guiLeft+xSize/2,guiTop+12,0xffffff);
        net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
        if(textures!=null)textures.draw(fontRenderer,x,y);for(GuiTextField f:fields)f.drawTextBox();
        if(pilotPage()){fontRenderer.drawString("Trigger channels",guiLeft+12,guiTop+61,0xffffff);for(int i=0;i<3;i++)fontRenderer.drawString(new String[]{"Block channels","Taxi channels","Hover channels"}[i],guiLeft+12,guiTop+91+i*28,0xffffff);}
        else if(sidePage()){}
        else if(d>=tile.displays.size()){fontRenderer.drawString("Label",guiLeft+12,guiTop+48,0xffffff);fontRenderer.drawString("Output channels",guiLeft+12,guiTop+88,0xffffff);}
        else fontRenderer.drawString("Gate channel (0 = always)",fields.get(0).x,guiTop+83,0xffffff);
    }
    /** A solid panel button has no vanilla 256-pixel atlas width limit. */
    private static final class CockpitButton extends GuiButton {
        CockpitButton(int id,int x,int y,int width,int height,String label){super(id,x,y,width,height,label);}
        @Override public void drawButton(net.minecraft.client.Minecraft mc,int mx,int my,float partial){
            if(!visible)return;hovered=mx>=x&&my>=y&&mx<x+width&&my<y+height;
            net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
            drawRect(x,y,x+width,y+height,enabled&&hovered?0xff64879d:0xff3c5363);
            drawRect(x+1,y+1,x+width-1,y+height-1,enabled&&hovered?0xff344e60:0xff243642);
            drawCenteredString(mc.fontRenderer,mc.fontRenderer.trimStringToWidth(displayString,width-8),x+width/2,y+(height-8)/2,enabled?0xffffff:0x8b969d);
        }
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
