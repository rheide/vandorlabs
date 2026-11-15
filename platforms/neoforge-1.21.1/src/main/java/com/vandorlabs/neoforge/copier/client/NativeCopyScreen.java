package com.vandorlabs.neoforge.copier.client;

import com.vandorlabs.copier.core.CopySettings;
import com.vandorlabs.neoforge.client.NativeIntegerSlider;
import com.vandorlabs.neoforge.copier.*;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Source Copy layout: height slider, channel increment, redstone bank and the two manual actions. */
public final class NativeCopyScreen extends AbstractContainerScreen<NativeCopyMenu> {
    private int heightChoice,increment,activation;private boolean chip,initializing;
    private ChannelList channels;private EditBox incrementField,channelsField;private NativeIntegerSlider height;
    private final Map<Integer,Button> buttons=new LinkedHashMap<>();
    public NativeCopyScreen(NativeCopyMenu menu,Inventory inventory,Component title){super(menu,inventory,title);var settings=menu.tile.settings();heightChoice=settings.height;increment=settings.increment;activation=settings.activation;channels=settings.channels;chip=settings.chip;}
    public Button control(int id){return buttons.get(id);}public EditBox incrementField(){return incrementField;}public EditBox channelsField(){return channelsField;}public NativeIntegerSlider heightSlider(){return height;}
    @Override protected void init(){imageWidth=Math.min(360,width-12);imageHeight=228;super.init();rebuild();}
    private void button(int id,int x,int y,int w,String caption){var button=Button.builder(Component.literal(caption),ignored->action(id,1)).bounds(leftPos+x,topPos+y,w,20).build();buttons.put(id,button);addRenderableWidget(button);}
    private void rebuild(){initializing=true;clearWidgets();buttons.clear();height=addRenderableWidget(new NativeIntegerSlider(leftPos+12,topPos+29,imageWidth-24,20,1,16,heightChoice,value->Component.literal("Copy height: "+value),value->{heightChoice=value;send(0);}));button(11,12,54,imageWidth-24,"Input: "+(chip?"Written Vehicle Chip":"Left Surface pad"));
        incrementField=new EditBox(font,leftPos+imageWidth-124,topPos+80,112,20,Component.literal("Channel increment"));incrementField.setMaxLength(11);incrementField.setValue(Integer.toString(increment));incrementField.setResponder(value->{try{int parsed=Integer.parseInt(value);new CopySettings(heightChoice,parsed,activation,channels,chip);increment=parsed;incrementField.setTextColor(0xffffff);send(0);}catch(IllegalArgumentException invalid){incrementField.setTextColor(0xff7777);}});addRenderableWidget(incrementField);
        button(0,12,106,138,"Redstone: "+new String[]{"Disabled","ON","OFF"}[activation]);channelsField=new EditBox(font,leftPos+158,topPos+106,imageWidth-170,20,Component.literal("Channels"));channelsField.setMaxLength(1024);channelsField.setValue(channels.toString());channelsField.setResponder(value->{try{channels=ChannelList.parse(value);channelsField.setTextColor(0xffffff);send(0);}catch(IllegalArgumentException invalid){channelsField.setTextColor(0xff7777);}});addRenderableWidget(channelsField);
        int third=(imageWidth-32)/3;button(1,12,196,third,"Copy");button(2,16+third,196,third,"Check areas");button(3,20+third*2,196,imageWidth-32-third*2,"Done");initializing=false;updateEnabled();}
    private CopySettings choices(){return new CopySettings(heightChoice,increment,activation,channels,chip);}
    private void send(int operation){if(!initializing)PacketDistributor.sendToServer(new NativeCopySettingsPayload(menu.containerId,menu.tile.getBlockPos(),operation,choices()));}
    public void action(int id,int direction){if(menu.tile.busy()&&id!=3)return;if(id==0){activation=Math.floorMod(activation+direction,3);send(0);rebuild();}else if(id==11){chip=!chip;send(0);rebuild();}else if(id==1||id==2)send(id);else if(id==3)onClose();}
    private void updateEnabled(){boolean editable=!menu.tile.busy();for(var entry:buttons.entrySet())entry.getValue().active=entry.getKey()==3||editable;height.active=editable;incrementField.setEditable(editable);channelsField.setEditable(editable);}
    @Override protected void containerTick(){super.containerTick();updateEnabled();}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1){for(int id:new int[]{0,11})if(buttons.get(id).isMouseOver(x,y)){action(id,-1);return true;}}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);return super.mouseReleased(x,y,button);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff19232c);graphics.drawString(font,title,leftPos+8,topPos+8,0xffffff,false);graphics.drawString(font,"Channel increment",leftPos+12,topPos+86,0xffdddddd,false);graphics.drawWordWrap(font,Component.literal(menu.tile.status()),leftPos+12,topPos+138,imageWidth-24,0xffb9d5df);if(!menu.tile.error().isEmpty())graphics.drawWordWrap(font,Component.literal(menu.tile.error()),leftPos+12,topPos+160,imageWidth-24,0xffff9999);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
