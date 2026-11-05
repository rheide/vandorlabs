package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.TriggerState;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.*;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native Trigger editor; family rules remain in the current shared dialog contract. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeTriggerScreen extends AbstractContainerScreen<TriggerMenu> {
    private TriggerDialog.State state;
    private ProgrammableDialogLayout panel;
    private List<TriggerDialog.Control> controls=List.of();
    private final NativeMaterialPicker[] artwork=new NativeMaterialPicker[4];
    private final NativeMaterialPicker sides;
    private EditBox channels;
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(VandorLabsBootstrap.TRIGGER_MENU.get(),NativeTriggerScreen::new);}
    public NativeTriggerScreen(TriggerMenu menu,Inventory inventory,Component title){
        super(menu,inventory,Component.literal(TriggerDialog.TITLE));
        TriggerState saved=menu.tile.triggerState();
        state=new TriggerDialog.State(new int[]{menu.tile.settings().mainTexture,saved.low,saved.medium,saved.on},saved.artworkModes(),saved.artworkSpeeds(),0,TriggerDialog.ARTWORK_PAGE,saved.exact,saved.bands,saved.frontOnly,saved.sides);
        for(int i=0;i<4;i++){final int slot=i;artwork[i]=new NativeMaterialPicker(state.texture(i),id->{var entry=ModernFinishCatalog.option(id);return entry.general()||entry.displaySelectable();},value->{state=state.withArtwork(slot,value);send();});}
        sides=new NativeMaterialPicker(state.sidesTexture,id->ModernFinishCatalog.option(id).general(),value->{state=state.withSides(value);send();});
    }
    public TriggerDialog.State editingState(){return state;}
    public ProgrammableDialogLayout panel(){return panel;}
    public EditBox channelField(){return channels;}
    public NativeMaterialPicker activePicker(){return state.page==TriggerDialog.REDSTONE_PAGE?null:state.page==TriggerDialog.SIDES_PAGE?sides:artwork[state.textureTab];}
    @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);rebuild();}
    private void rebuild(){
        String draft=channels==null?(menu.tile.getRedstoneChannels().isEmpty()?"":menu.tile.getRedstoneChannels().toString()):channels.getValue();
        clearWidgets();controls=TriggerDialog.controls(panel,state,Component.translatable("gui.done").getString());
        for(var control:controls){if(control.tab)continue;Button button=Button.builder(Component.literal(control.label),ignored->action(control.id,1)).bounds(control.x,control.y,control.width,control.height).build();button.active=control.enabled;addRenderableWidget(button);}
        var box=TriggerDialog.channelBox(panel);channels=new EditBox(font,box.x,box.y,box.width,box.height,Component.literal(TriggerDialog.CHANNEL_LABEL));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setFilter(value->value.matches("[0-9, ]*"));channels.setValue(draft);channels.visible=state.page==TriggerDialog.REDSTONE_PAGE;channels.active=channels.visible;channels.setResponder(ignored->send());addRenderableWidget(channels);
        validateChannels();NativeMaterialPicker picker=activePicker();if(picker!=null){picker.layout(panel.listX,TriggerDialog.listY(panel),panel.listWidth,TriggerDialog.listRows(panel),false);addRenderableWidget(picker.searchField());}
    }
    private ChannelList validateChannels(){var parsed=ChannelList.parse(channels.getValue());channels.setTextColor(parsed==null?0xFF7777:0xE0E0E0);return parsed;}
    private boolean send(){
        ChannelList parsed=validateChannels();if(parsed==null)return false;
        TriggerState saved=new TriggerState(state.texture(3),state.states,state.exact,state.texture(1),state.texture(2),state.frontOnly,state.sidesTexture,state.modes(),state.speeds());
        PacketDistributor.sendToServer(new TriggerPayload(menu.tile.getBlockPos(),menu.containerId,state.texture(0),saved,parsed));return true;
    }
    public void choose(int choice){NativeMaterialPicker picker=activePicker();if(picker!=null)picker.select(choice,false);state=state.choose(choice);send();}
    public void action(int operation,int direction){
        if(operation==TriggerDialog.DONE){if(send())onClose();return;}
        if(!TriggerDialog.handles(operation))return;
        NativeMaterialPicker picker=activePicker();if(picker!=null)picker.closeInventory();state=state.action(operation,direction);
        if(TriggerDialog.sendsImmediately(operation))send();if(TriggerDialog.rebuilds(operation)||operation==TriggerDialog.EXACT)rebuild();
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0||button==1)for(var control:controls)if(control.enabled&&(button==0||control.id==TriggerDialog.BANDS||control.id==TriggerDialog.EXACT||control.id==TriggerDialog.FRONT_ONLY)&&x>=control.x&&x<control.x+control.width&&y>=control.y&&y<control.y+control.height){action(control.id,button==1?-1:1);return true;}
        NativeMaterialPicker picker=activePicker();return picker!=null&&picker.click(x,y,button)||super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){var picker=activePicker();return picker!=null&&picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){for(var picker:artwork)picker.release();sides.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){var picker=activePicker();return picker!=null&&picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){var picker=activePicker();if(picker!=null&&picker.keyPressed(key,scan,modifiers))return true;if(key==257||key==335){if(send())onClose();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){
        graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF101012);graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+24,0xFF202028);graphics.drawString(font,title,panel.left+12,panel.top+8,0xFFFFFFFF,false);
        for(var control:controls)if(control.tab){graphics.fill(control.x,control.y,control.x+control.width,control.y+control.height,control.enabled?0xFF203540:0xFF365568);graphics.drawCenteredString(font,control.label,control.x+control.width/2,control.y+5,0xFFE0F0FF);}
        var picker=activePicker();if(picker!=null)picker.render(graphics,mouseX,mouseY);else graphics.drawString(font,TriggerDialog.CHANNEL_LABEL,panel.left+12,TriggerDialog.channelLabelY(panel),0xDAE8F0,false);
    }
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
