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
    private ReceiverPane receiverPane;
    private boolean surfaceRedstone,surfaceControls;
    private RedstoneRowsPane rowsPane;
    @Override protected HousingTextureList materialPicker(){return textures;}
    private int page;
    private boolean redstonePage;
    private int category(){if(redstonePage)return 5;if(pilotPage())return 3;if(sidePage())return 4;int d=displayIndex();return d<tile.displays.size()?("touch".equals(tile.displays.get(d).role)?1:0):2;}
    private java.util.List<Integer> categoryPages(int category){java.util.List<Integer> values=new ArrayList<>();for(int i=0;i<pages();i++){int old=page;boolean red=redstonePage;page=i;redstonePage=false;if(category()==category&&(controlIndex()<0||!tile.controls.get(controlIndex()).id.contains("/")))values.add(i);page=old;redstonePage=red;}return values;}
    private boolean seating(){return tile.data().seat!=null;}
    private boolean pilotPage(){return seating()&&page==0;}
    private boolean sidePage(){return page==(seating()?1:0);}
    private int displayIndex(){return page-(seating()?1:0)-1;}
    private int controlIndex(){return displayIndex()-tile.displays.size();}
    private int pages(){return (seating()?1:0)+1+tile.displays.size()+tile.controls.size();}
    public GuiCockpit(ContainerCockpit container){super(container);cockpit=container;tile=container.tile;page=0;for(int category=0;category<5;category++){java.util.List<Integer> candidates=categoryPages(category);if(!candidates.isEmpty()){page=candidates.get(0);break;}}xSize=580;ySize=280;}
    @Override public void initGui(){
        xSize=Math.min(580,width-12);ySize=Math.min(280,height-12);super.initGui();buttonList.clear();fields.clear();textures=null;receiverPane=null;rowsPane=null;org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        String[] categories={"Screen","Touch","Throttle","Pilot","Casing","Redstone"};
        int tabX=guiLeft+12,selected=category();
        for(int i=0;i<6;i++){if(i!=5&&categoryPages(i).isEmpty())continue;int tabWidth=fontRenderer.getStringWidth(categories[i])+7;GuiButton tab=new DialogTabButton(200+i,tabX,guiTop+28,tabWidth,20,categories[i]);tabX+=tabWidth+2;tab.enabled=selected!=i;buttonList.add(tab);}
        java.util.List<Integer> children=categoryPages(selected);int childWidth=children.isEmpty()?70:Math.min(110,(xSize-24)/children.size());
        for(int i=0;i<children.size();i++){int child=children.get(i),display=child-(seating()?1:0)-1,control=display-tile.displays.size();String caption=display>=0&&display<tile.displays.size()?tile.displays.get(display).id:control>=0?tile.controls.get(control).id:"";if(children.size()>1){GuiButton tab=new DialogTabButton(300+child,guiLeft+12+i*childWidth,guiTop+50,childWidth-2,18,fontRenderer.trimStringToWidth(caption.replace("screen_","").replace("touch_","").replace("throttle_",""),childWidth-6));tab.enabled=child!=page;buttonList.add(tab);}}
        buttonList.add(new CockpitButton(2,guiLeft+xSize-92,guiTop+ySize-26,84,20,"Done"));
        int d=displayIndex(),c=controlIndex(),x=guiLeft+12,y=guiTop+76,w=xSize-24;
        if(redstonePage){buttonList.add(new CockpitButton(90,x,guiTop+68,Math.min(240,w),20,"Transmitter..."));
        }else if(pilotPage()){
            buttonList.add(new CockpitButton(10,x,guiTop+74,w/2-2,20,"Taxi on boarding: "+(tile.isTaxiMode()?"On":"Off")));
            buttonList.add(new RedstoneActivationButton(11,x+w/2+2,guiTop+74,w/2-2,tile.getRedstoneMode()));
            text(0,x,guiTop+112,w/2-3,ChannelFields.text(tile.getRedstoneChannels()));
            for(int i=0;i<3;i++)text(i+1,x+((i+1)%2)*(w/2+3),guiTop+112+((i+1)/2)*40,w/2-3,ChannelFields.text(tile.modeChannels(i)));
            for(int i=0;i<3;i++)buttonList.add(new CockpitButton(20+i,x+i*w/3,guiTop+ySize-50,w/3-2,20,new String[]{"Park mode","Taxi mode","Hover mode"}[i]));
        }else if(sidePage()){
            int top=tile.design().equals("chair_siderests")||tile.design().equals("cockpit")?100:76;
            if(tile.design().equals("cockpit"))buttonList.add(new CockpitButton(13,x,guiTop+76,w,20,"Throttles: "+(tile.throttleCount()==1?"Single":"Twin")));
            if(tile.design().equals("chair_siderests"))buttonList.add(new CockpitButton(12,x,guiTop+76,w,20,"Height: +"+(tile.getHeight()*2)+"px"));
            textures=new HousingTextureList(x,guiTop+top,w-8,tile.sideTexture(),8).visibleRows(Math.max(2,(ySize-top-34)/HousingTextureList.ROW_HEIGHT));
            buttonList.add(new CockpitButton(34,x,guiTop+ySize-26,110,20,"Default casing"));
        }else if(d<tile.displays.size()){
            TileEntityCockpit.Display screen=tile.displays.get(d);boolean input="touch".equals(screen.role);int controlW=Math.min(154,(xSize-30)/2),cx=guiLeft+xSize-controlW-12;
            GuiButton displayTab=new DialogTabButton(400,x,guiTop+74,76,18,"Display"),receiveTab=new DialogTabButton(401,x+160,guiTop+74,84,18,"Redstone"),controlsTab=new DialogTabButton(402,x+80,guiTop+74,76,18,"Controls");displayTab.enabled=surfaceRedstone||surfaceControls;receiveTab.enabled=!surfaceRedstone;controlsTab.enabled=!surfaceControls;buttonList.add(displayTab);buttonList.add(receiveTab);buttonList.add(controlsTab);
            if(surfaceControls){net.minecraft.nbt.NBTTagList values=new net.minecraft.nbt.NBTTagList();for(int row=0;row<screen.rows;row++)values.appendTag(tile.control(screen.id+"/"+row).write());final int display=d;rowsPane=new RedstoneRowsPane(fontRenderer,screen.maxRows(),false,values,row->false,rows->applyRows(display,rows));rowsPane.layout(x,guiTop+98,w,ySize-124,buttonList);}
            else if(surfaceRedstone){receiverPane=new ReceiverPane(40,32,fontRenderer,x,guiTop+104,Math.min(240,w),screen.receiveChannels(),screen.activation());buttonList.add(receiverPane.button());}
            else{
                textures=new HousingTextureList(x,guiTop+100,cx-x-10,screen.texture,Math.max(2,(ySize-134)/HousingTextureList.ROW_HEIGHT),null,
                        value->HousingTextureList.generalTexture(value)||ScreenTextureList.artwork(value,input)).visibleRows(Math.max(2,(ySize-134)/HousingTextureList.ROW_HEIGHT)).custom(this::choose)
                        .redstone(this::redstone).vehicle(()->choose(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.VEHICLE_SURFACE));
                buttonList.add(new CockpitButton(30,cx,guiTop+104,controlW,20,"Animation: "+(screen.animated?"On":"Off")));
                buttonList.add(new CockpitButton(31,cx,guiTop+128,controlW,20,"Speed: "+new String[]{"Slow","Normal","Fast"}[screen.speed]));
                buttonList.add(new CockpitButton(33,cx,guiTop+152,controlW,20,"Blank surface"));
            }
        }else{
            TileEntityCockpit.Control control=tile.controls.get(c);
            text(0,x,y+12,w,control.label);text(1,x,y+52,w,ChannelFields.text(control.channels));
            if(control.isThrottle()){
                buttonList.add(new CockpitButton(42,x,y+84,w/2-2,20,"Low: "+control.min));buttonList.add(new CockpitButton(43,x+w/2+2,y+84,w/2-2,20,"High: "+control.max));
            }else buttonList.add(new CockpitButton(40,x,y+84,w,20,"Control: "+new String[]{"Toggle","Slider","Pulse (1 second)"}[control.kind]));
            buttonList.add(new CockpitButton(41,x,y+106,w,20,(control.isThrottle()?new String[]{"Off","Low","Medium","High"}[control.step()]+": ":"Output: ")+control.level+"  (operate)"));
        }
    }
    int inlineTransmitterSelector(){return 0;}
    private void text(int id,int x,int y,int width,String value){GuiTextField field=new GuiTextField(id,fontRenderer,x,y,width,18);field.setMaxStringLength(id==0?128:256);field.setText(value);fields.add(field);}
    private void packet(int op,NBTTagCompound values){com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageCockpit(inventorySlots.windowId,op,values));}
    private void redstone(){choose(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.REDSTONE_SURFACE);surfaceControls=true;surfaceRedstone=false;initGui();}
    private void applyRows(int display,net.minecraft.nbt.NBTTagList rows){if(!tile.configureRows(display,rows))return;NBTTagCompound tag=new NBTTagCompound();tag.setInteger("RedstoneDisplay",display);tag.setTag("Rows",rows);packet(0,tag);}
    private void choose(int value){if(sidePage())tile.setSideTexture(value);else tile.displays.get(displayIndex()).texture=value;save();}
    private void save(){
        if(rowsPane!=null){rowsPane.save();return;}if(redstonePage)return;
        int d=displayIndex(),c=controlIndex();NBTTagCompound tag;
        if(pilotPage()){if(fields.size()!=4)return;for(GuiTextField f:fields)if(ChannelList.parse(f.getText())==null){f.setTextColor(0xff7777);return;}tag=new NBTTagCompound();tag.setBoolean("Pilot",true);tag.setString("Channels",fields.get(0).getText());tag.setBoolean("Taxi",tile.isTaxiMode());tag.setInteger("Trigger",tile.getRedstoneMode());for(int i=0;i<3;i++)tag.setString("Mode"+i,fields.get(i+1).getText());
        }else if(sidePage()){
            tag=new NBTTagCompound();tag.setInteger("SideTexture",tile.sideTexture());if(tile.design().equals("cockpit"))tag.setInteger("ThrottleCount",tile.throttleCount());if(tile.design().equals("chair_siderests"))tag.setInteger("Height",tile.getHeight());
        }else if(d<tile.displays.size()){
            TileEntityCockpit.Display screen=tile.displays.get(d);if(receiverPane!=null){ChannelList channels=receiverPane.channels();if(channels==null)return;screen.receiver(channels,screen.activation());}
            tag=screen.write();tag.setInteger("Display",d);
        }else{
            if(ChannelList.parse(fields.get(1).getText())==null){fields.get(1).setTextColor(0xff7777);return;}
            TileEntityCockpit.Control control=tile.controls.get(c);tag=new NBTTagCompound();tag.setInteger("Control",c);tag.setString("Label",fields.get(0).getText());tag.setString("Channels",fields.get(1).getText());tag.setInteger("Kind",control.kind);if(control.isThrottle()){tag.setInteger("Min",control.min);tag.setInteger("Max",control.max);}
        }
        for(GuiTextField f:fields)f.setTextColor(0xe0e0e0);packet(0,tag);
    }
    @Override protected void actionPerformed(GuiButton button){
        if(button.id>=400&&button.id<=402){save();surfaceRedstone=button.id==401;surfaceControls=button.id==402;initGui();return;}
        if(button.id>=200&&button.id<206){save();surfaceRedstone=false;surfaceControls=false;int category=button.id-200;redstonePage=category==5;if(!redstonePage){java.util.List<Integer> children=categoryPages(category);if(!children.isEmpty())page=children.get(0);}initGui();return;}
        if(button.id>=300&&button.id<400){save();surfaceRedstone=false;surfaceControls=false;redstonePage=false;page=button.id-300;initGui();return;}
        if(rowsPane!=null&&rowsPane.action(button))return;
        if(button.id==90)return;
        if(button.id==2){save();mc.player.closeScreen();return;}
        if(button.id==0 || button.id==1){save();page=Math.floorMod(page+(button.id==0?-1:1)*GuiOptionCycle.direction(),pages());initGui();return;}
        int d=displayIndex(),c=controlIndex();
        if(button.id==10)tile.setTaxiMode(!tile.isTaxiMode());
        else if(button.id==11)tile.configure(GuiOptionCycle.next(tile.getRedstoneMode(),3),tile.getHeight(),mc.world);
        else if(button.id==12)tile.setHeight(GuiOptionCycle.next(tile.getHeight(),4));
        else if(button.id==13)tile.setThrottleCount(GuiOptionCycle.next(tile.throttleCount(),1,2));
        else if(button.id>=20 && button.id<=22){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Mode",button.id-20);packet(2,tag);return;}
        else if(button.id==30)tile.displays.get(d).animated=!tile.displays.get(d).animated;
        else if(button.id==31)tile.displays.get(d).speed=GuiOptionCycle.next(tile.displays.get(d).speed,3);
        else if(button.id==32){TileEntityCockpit.Display display=tile.displays.get(d);ChannelList channels=receiverPane.channels();if(channels==null)return;display.receiver(channels,GuiOptionCycle.next(display.activation(),3));}
        else if(button.id==33)tile.displays.get(d).texture=-1;
        else if(button.id==34)tile.setSideTexture(-1);
        else if(button.id==40)tile.controls.get(c).kind=GuiOptionCycle.next(tile.controls.get(c).kind,3);
        else if(button.id==42){TileEntityCockpit.Control value=tile.controls.get(c);value.min=GuiOptionCycle.next(value.min,1,value.max-2);}
        else if(button.id==43){TileEntityCockpit.Control value=tile.controls.get(c);value.max=GuiOptionCycle.next(value.max,value.min+2,15);}
        else if(button.id==41){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Control",c);tag.setDouble("Column",tile.controls.get(c).nextColumn());packet(1,tag);return;}
        save();initGui();
    }
    @Override protected void keyTyped(char ch,int key)throws IOException{boolean consumed=rowsPane!=null&&rowsPane.keyTyped(ch,key)||receiverPane!=null&&receiverPane.keyTyped(ch,key);for(GuiTextField f:fields)consumed|=f.textboxKeyTyped(ch,key);if(consumed)save();else super.keyTyped(ch,key);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0,1,10,11,12,13,30,31,32,40,42,43,604,605,606))return;if(rowsPane!=null)rowsPane.mouseClicked(x,y,button);if(receiverPane!=null)receiverPane.mouseClicked(x,y,button);for(GuiTextField f:fields)if(f.getVisible())f.mouseClicked(x,y,button);HousingTextureList clickedTextures=textures;if(clickedTextures!=null && clickedTextures.click(x,y,button)){if(textures==clickedTextures && clickedTextures.picked())choose(clickedTextures.selected());return;}super.mouseClicked(x,y,button);}
    @Override public void updateScreen(){if(rowsPane!=null)rowsPane.update();if(receiverPane!=null)receiverPane.field().updateCursorCounter();for(GuiTextField f:fields)f.updateCursorCounter();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xff304858);DialogHeader.logo(guiLeft,guiTop,xSize,28);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);int d=displayIndex(),c=controlIndex();String title=tile.getBlockType().getLocalizedName();drawCenteredString(fontRenderer,fontRenderer.trimStringToWidth(title,xSize-128),guiLeft+xSize/2-10,guiTop+12,0xffffff);
        net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
        if(textures!=null)textures.draw(fontRenderer,x,y);for(GuiTextField f:fields)f.drawTextBox();if(receiverPane!=null)receiverPane.draw();if(rowsPane!=null)rowsPane.draw();
        if(redstonePage){fontRenderer.drawString("Range for all cockpit controls",guiLeft+12,guiTop+54,0xffffff);fontRenderer.drawString("Target channels are edited on each control page.",guiLeft+12,guiTop+144,0xdae8f0);fontRenderer.drawString("Blank targets use the shared channel defaults.",guiLeft+12,guiTop+156,0xdae8f0);}
        else if(pilotPage()){fontRenderer.drawString("Trigger channels",guiLeft+12,guiTop+101,0xffffff);for(int i=0;i<3;i++)fontRenderer.drawString(new String[]{"Block channels","Taxi channels","Hover channels"}[i],fields.get(i+1).x,fields.get(i+1).y-11,0xffffff);}
        else if(sidePage()){}
        else if(d>=tile.displays.size()){fontRenderer.drawString("Label",guiLeft+12,guiTop+76,0xffffff);fontRenderer.drawString("Target channels (blank = shared defaults)",guiLeft+12,guiTop+116,0xffffff);TileEntityCockpit.Control value=tile.controls.get(c);if(value.isThrottle()&&ySize>=270)fontRenderer.drawString("Off 0 / Medium "+((value.min+value.max+1)/2)+" - four positions",guiLeft+12,guiTop+230,0xdae8f0);}

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
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();if(rowsPane!=null)rowsPane.wheel(org.lwjgl.input.Mouse.getEventDWheel());}
    @Override public boolean doesGuiPauseGame(){return false;}
}
