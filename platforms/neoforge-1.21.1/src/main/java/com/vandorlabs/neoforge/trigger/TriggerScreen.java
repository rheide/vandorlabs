package com.vandorlabs.neoforge.trigger;

import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import com.vandorlabs.ui.TriggerDialog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TriggerScreen extends AbstractContainerScreen<TriggerMenu> {
    private ProgrammableDialogLayout panel;
    private TriggerDialog.State state;
    private final int[] finishes=new int[4];
    private NativeMaterialPicker[] pickers;
    private EditBox channels;
    public TriggerScreen(TriggerMenu menu,Inventory inventory,Component ignored){super(menu,inventory,Component.literal(TriggerDialog.TITLE));var value=menu.tile.state();state=new TriggerDialog.State(0,value.bands,value.exact);finishes[0]=menu.tile.settings().mainTexture;finishes[1]=value.low;finishes[2]=value.medium;finishes[3]=value.on;}
    @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event){event.register(TriggerMenu.TYPE.get(),TriggerScreen::new);}
    @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);var bounds=TriggerDialog.channels(panel);channels=new EditBox(font,bounds.x,bounds.y,bounds.width,bounds.height,Component.literal("Channels (0 = none)"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setFilter(text->text.matches("[0-9, ]*"));channels.setValue(menu.tile.getRedstoneChannels().toString());channels.setResponder(text->send());pickers=new NativeMaterialPicker[4];for(int i=0;i<4;i++){final int tab=i;pickers[i]=new NativeMaterialPicker(panel.listX,panel.top+54,panel.listWidth,panel.rows(true),finishes[i],choice->{finishes[tab]=choice;send();});}controls();}
    private void controls(){clearWidgets();addRenderableWidget(channels);for(var control:TriggerDialog.controls(panel,state)){var b=control.bounds;Button button=Button.builder(Component.literal(b.id>=90&&b.id<=93?font.plainSubstrByWidth(b.label,b.width-4):b.label),ignored->action(b.id,1)).bounds(b.x,b.y,b.width,b.height).build();button.visible=control.visible;button.active=control.enabled;addRenderableWidget(button);}}
    private void send(){ChannelList parsed=ChannelList.parse(channels.getValue());channels.setTextColor(parsed==null?0xFF7777:0xE0E0E0);if(parsed!=null)PacketDistributor.sendToServer(new TriggerPayload(menu.containerId,menu.pos,finishes[0],finishes[3],finishes[1],finishes[2],state.bands,state.exact,parsed.toString()));}
    private void action(int id,int direction){if(id==100){if(ChannelList.parse(channels.getValue())!=null){send();onClose();}return;}state=state.action(id,direction);controls();if(id==101||id==102)send();}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1)for(var control:TriggerDialog.controls(panel,state))if(control.visible&&control.enabled&&(control.bounds.id==101||control.bounds.id==102)&&control.bounds.contains(x,y)){action(control.bounds.id,-1);return true;}return pickers[state.tab].click(x,y,button)||super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return pickers[state.tab].drag(y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){for(var picker:pickers)picker.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return pickers[state.tab].wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257||key==335){action(100,1);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF101012);g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+24,0xFF202028);g.drawString(font,title,panel.left+12,panel.top+8,0xFFFFFF,false);pickers[state.tab].draw(g,font,x,y);g.drawString(font,"Channels (0 = none)",panel.controlsX,panel.top+30,0xDAE8F0,false);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
