package com.vandorlabs.neoforge.plate.client;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.client.*;
import com.vandorlabs.neoforge.plate.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.DialogInput;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
/** The standard categorized picker, scrollbar/wheel and inline transmitter editor. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativePlateScreen extends AbstractContainerScreen<NativePlateMenu> {
    private int texture,mode,sensitivity,page;private final NativeMaterialPicker picker;
    private NativeInlineTransmissionWidget transmission;private final Map<Integer,Button> options=new HashMap<>();
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){if(NativePlateBindings.enabled())event.register(NativePlateBindings.MENU,NativePlateScreen::new);}
    public NativePlateScreen(NativePlateMenu menu,Inventory inventory,Component title){super(menu,inventory,title);texture=menu.tile.texture();mode=menu.tile.mode();sensitivity=menu.tile.sensitivity();picker=new NativeMaterialPicker(texture,id->{var entry=ModernFinishCatalog.option(id);return entry.general()||entry.category().startsWith("Trapdoors");},value->{texture=value;send();});}
    @Override protected void init(){imageWidth=Math.min(450,width-12);imageHeight=Math.min(306,height-12);super.init();if(transmission==null)transmission=new NativeInlineTransmissionWidget(font,leftPos+12,topPos+72,imageWidth-24,menu.tile.getRedstoneChannels(),menu.tile.transmissionRange(),menu.tile.localRedstone(),(channels,range,local)->PacketDistributor.sendToServer(new NativePlatePayload(menu.containerId,texture,mode,sensitivity,channels,range,local,true)));else transmission.layout(leftPos+12,topPos+72,imageWidth-24);rebuild();}
    private void rebuild(){clearWidgets();options.clear();if(page==0){int listWidth=Math.max(140,(imageWidth-36)*2/3);picker.layout(leftPos+12,topPos+62,listWidth,Math.max(3,(imageHeight-100)/22),false);addRenderableWidget(picker.searchField());int x=leftPos+24+listWidth,w=imageWidth-listWidth-36;add(1,x,topPos+64,w,"Output: "+new String[]{"Disabled","Pressed","Released"}[mode]);add(2,x,topPos+90,w,"Sensitivity: "+new String[]{"Wood","Gold","Iron"}[sensitivity]);}else for(var control:transmission.controls())addRenderableWidget(control);addRenderableWidget(com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.translatable("gui.done"),ignored->onClose()).bounds(leftPos+imageWidth-100,topPos+imageHeight-28,88,20).build());}
    private void add(int id,int x,int y,int width,String text){var button=com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.literal(text),ignored->act(id,1)).bounds(x,y,width,20).build();options.put(id,button);addRenderableWidget(button);}
    private void act(int id,int direction){if(id==1)mode=DialogInput.cycle(mode,3,direction);if(id==2)sensitivity=DialogInput.cycle(sensitivity,3,direction);send();rebuild();}
    private void send(){PacketDistributor.sendToServer(new NativePlatePayload(menu.containerId,texture,mode,sensitivity,ChannelList.EMPTY,16,true,false));}
    public int panelLeft(){return leftPos;}public NativeInlineTransmissionWidget transmissionWidget(){return transmission;}
    public Button option(int id){return options.get(id);}public NativeMaterialPicker picker(){return picker;}public int page(){return page;}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&y>=topPos+32&&y<topPos+52&&x>=leftPos+12&&x<leftPos+imageWidth-12){int next=x<leftPos+imageWidth/2?0:1;if(next!=page){if(page==1)transmission.hide();else picker.closeInventory();page=next;rebuild();}return true;}if(button==1)for(var entry:options.entrySet())if(entry.getValue().isMouseOver(x,y)){act(entry.getKey(),-1);return true;}if(page==0&&picker.click(x,y,button)||page==1&&transmission.mouseClicked(x,y,button))return true;return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return page==0&&picker.drag(x,y)||NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){picker.release();NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return page==0&&picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){return page==0&&picker.keyPressed(key,scan,modifiers)||page==1&&transmission.specialKey(key)||super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){if(transmission!=null)transmission.flush();super.onClose();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){com.vandorlabs.neoforge.client.NativeDialogVisuals.panel(g,font,title,leftPos,topPos,imageWidth,imageHeight,com.vandorlabs.ui.DialogStyle.PROGRAMMABLE);for(int tab=0;tab<2;tab++){int x=leftPos+12+tab*(imageWidth-24)/2,w=(imageWidth-24)/2;com.vandorlabs.neoforge.client.NativeDialogVisuals.tab(g,font,tab==0?"General":"Redstone",x,topPos+32,w,20,page!=tab,mouseX,mouseY);}if(page==0)picker.render(g,mouseX,mouseY);else{transmission.renderLabels(g,font);g.drawWordWrap(font,Component.literal("Wood: On/Off. Gold: full power at 15 entities. Iron: full power at 150."),leftPos+12,topPos+146,imageWidth-24,0xFFC0C8D0);}}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}@Override public boolean isPauseScreen(){return false;}
}
