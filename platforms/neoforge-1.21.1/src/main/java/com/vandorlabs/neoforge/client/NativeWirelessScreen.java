package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.DialogInput;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Immediate endpoint settings; transmitter drafts and receiver channels retain their different editors. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeWirelessScreen extends AbstractContainerScreen<WirelessMenu> {
    private final boolean transmitter;
    private int page,on,off,face;
    private boolean analog,ready,dirty,closing;
    private EditBox channels;
    private NativeInlineTransmissionWidget transmission;
    private NativeIntegerSlider onSlider,offSlider;
    private final Map<Integer,Button> options=new HashMap<>();
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(VandorLabsBootstrap.WIRELESS_MENU.get(),NativeWirelessScreen::new);}
    public NativeWirelessScreen(WirelessMenu menu,Inventory inventory,Component title){super(menu,inventory,Component.literal(menu.tile.transmitter()?"Redstone Transmitter Block":"Redstone Receiver Block"));transmitter=menu.tile.transmitter();analog=menu.tile.analog();on=menu.tile.onLevel();off=menu.tile.offLevel();face=menu.tile.port().get3DDataValue();}
    public int page(){return page;}public int panelLeft(){return leftPos;}public int panelTop(){return topPos;}public int panelWidth(){return imageWidth;}public int panelHeight(){return imageHeight;}
    public EditBox receiveField(){return channels;}public NativeInlineTransmissionWidget inlineWidget(){return transmission;}public NativeIntegerSlider onControl(){return onSlider;}public NativeIntegerSlider offControl(){return offSlider;}public Button option(int id){return options.get(id);}
    @Override protected void init(){imageWidth=Math.min(280,width-12);imageHeight=Math.min(transmitter?218:248,height-8);super.init();ready=false;
        if(transmitter){if(transmission==null)transmission=new NativeInlineTransmissionWidget(font,leftPos+12,topPos+70,imageWidth-24,menu.tile.transmissionChannels(),menu.tile.transmissionRange(),menu.tile.localRedstone(),(selected,range,local)->PacketDistributor.sendToServer(new WirelessTransmissionPayload(menu.tile.getBlockPos(),menu.containerId,selected,range,local)));else {transmission.flush();transmission.layout(leftPos+12,topPos+70,imageWidth-24);}}
        else {if(channels==null){channels=new EditBox(font,leftPos+12,topPos+72,imageWidth-24,18,Component.literal("Receive channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setFilter(value->value.matches("[0-9, ]*"));channels.setValue(menu.tile.getRedstoneChannels().isEmpty()?"":menu.tile.getRedstoneChannels().toString());channels.setResponder(value->{if(ready)save();});}else {channels.setPosition(leftPos+12,topPos+72);channels.setWidth(imageWidth-24);}}
        rebuild();ready=true;
    }
    private void rebuild(){boolean previous=ready;ready=false;setFocused(null);clearWidgets();options.clear();onSlider=null;offSlider=null;
        if(page==0)addOption(2,66,(transmitter?"Input":"Output")+" port: "+Direction.from3DDataValue(face).getSerializedName());
        else {if(transmitter)for(var control:transmission.controls())addRenderableWidget(control);else addRenderableWidget(channels);addOption(1,transmitter?140:102,"Signal: "+(analog?"Analog":"Digital"));
            if(!transmitter){onSlider=new NativeIntegerSlider(leftPos+12,topPos+128,imageWidth-24,20,0,15,on,value->Component.literal("On level: "+value),value->{on=value;if(ready)dirty=true;});onSlider.active=!analog;addRenderableWidget(onSlider);offSlider=new NativeIntegerSlider(leftPos+12,topPos+154,imageWidth-24,20,0,15,off,value->Component.literal("Off level: "+value),value->{off=value;if(ready)dirty=true;});addRenderableWidget(offSlider);}}
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),ignored->onClose()).bounds(leftPos+12,topPos+imageHeight-30,imageWidth-24,20).build());ready=previous;
    }
    private void addOption(int id,int y,String label){Button button=Button.builder(Component.literal(label),ignored->act(id,1)).bounds(leftPos+12,topPos+y,imageWidth-24,20).build();options.put(id,button);addRenderableWidget(button);}
    private void act(int id,int direction){if(id==1)analog=!analog;else if(id==2)face=DialogInput.cycle(face,6,direction);save();rebuild();}
    private boolean save(){if(!ready)return false;ChannelList receive=transmitter?ChannelList.EMPTY:ChannelList.parse(channels.getValue());if(!transmitter)channels.setTextColor(receive==null?0xFF7777:0xE0E0E0);if(receive==null)return false;PacketDistributor.sendToServer(new WirelessPayload(menu.tile.getBlockPos(),menu.containerId,receive,analog,on,off,Direction.from3DDataValue(face)));dirty=false;return true;}
    @Override protected void containerTick(){super.containerTick();if(dirty)save();}
    @Override public void onClose(){if(!closing){closing=true;if(transmission!=null)transmission.flush();save();}super.onClose();}
    @Override public void removed(){if(!closing){closing=true;if(transmission!=null)transmission.flush();save();}super.removed();}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0)for(int tab=0;tab<2;tab++){int left=leftPos+(tab==0?12:128),span=tab==0?112:140;if(x>=left&&x<left+span&&y>=topPos+32&&y<topPos+52){if(page!=tab){if(transmission!=null)transmission.hide();page=tab;rebuild();}return true;}}
        if(button==1)for(var entry:options.entrySet())if(entry.getValue().isMouseOver(x,y)){entry.getValue().playDownSound(minecraft.getSoundManager());act(entry.getKey(),-1);return true;}
        if(page==1&&transmission!=null&&transmission.mouseClicked(x,y,button))return true;return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);return super.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(page==1&&transmission!=null&&transmission.specialKey(key))return true;if(key==257||key==335)return true;return super.keyPressed(key,scan,modifiers);}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF101012);graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+24,0xFF202028);graphics.drawString(font,title,leftPos+12,topPos+8,0xFFFFFFFF,false);
        for(int tab=0;tab<2;tab++){int left=leftPos+(tab==0?12:128),span=tab==0?112:140;graphics.fill(left,topPos+32,left+span,topPos+52,page==tab?0xFF365568:0xFF203540);graphics.drawCenteredString(font,tab==0?"General":"Redstone",left+span/2,topPos+37,0xFFE0F0FF);}
        if(page==1){if(transmission!=null)transmission.renderLabels(graphics,font);else {graphics.drawString(font,"Receive channels",leftPos+12,topPos+60,0xFFDAE8F0,false);if(analog)graphics.drawString(font,"On level follows received signal",leftPos+12,topPos+180,0xFFC9D9E2,false);}}
    }
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
