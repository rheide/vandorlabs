package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.DialogInput;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import com.vandorlabs.ui.ScreenSelectorDialog;
import com.vandorlabs.ui.TextureListLayout;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ScreenClient {
    private ScreenClient(){}
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent event){event.<ScreenMenu,AbstractContainerScreen<ScreenMenu>>register(ScreenRegistration.MENU.get(),(menu,inventory,title)->menu.redstoneEditor?new RedstoneConfigurationScreen(menu,inventory,title):new Selector(menu,inventory,title));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(ScreenRegistration.TILE.get(),context->new ScreenRenderer());}
    @SubscribeEvent public static void resources(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event){event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->ScreenRenderer.clearResources());}
    @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
    public static final class ClockEvents {
        @SubscribeEvent public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event){ScreenRenderer.tickClock();}
    }
    public static final class Selector extends AbstractContainerScreen<ScreenMenu> {
        private static final int ARTWORK_BASE=1000000,REDSTONE_ROW=-100001;
        private final ProgrammableScreenEntity tile;
        private ProgrammableDialogLayout panel;
        private ScreenSelectorDialog.State state;
        private String artwork,channelText;
        private int housing,surface;
        private boolean centered;
        private EditBox channel;
        private com.vandorlabs.neoforge.client.NativeMaterialPicker picker;
        private List<ScreenSelectorDialog.Control> controls=List.of();
        public Selector(ScreenMenu menu,Inventory inventory,Component title){
            super(menu,inventory,title);tile=menu.tile;state=tile.dialogState(0);artwork=tile.data().selectedScreen;housing=tile.data().housingTexture;surface=tile.surface();channelText=tile.getRedstoneChannels().toString();
        }
        @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);rebuild();picker.centerSelected();}
        private String translated(String key){return Component.translatable(key).getString();}
        private void rebuild(){
            clearWidgets();controls=ScreenSelectorDialog.controls(panel,state,this::translated);
            for(var control:controls){var b=control.bounds;String label=b.id>=90?font.plainSubstrByWidth(b.label,b.width-4):b.label;Button button=Button.builder(Component.literal(label),ignored->action(b.id,1)).bounds(b.x,b.y,b.width,b.height).build();button.active=control.enabled;addRenderableWidget(button);}
            var b=ScreenSelectorDialog.channelBounds(panel);channel=new EditBox(font,b.x,b.y,b.width,b.height,Component.translatable("gui.vandorlabs.selector.channel"));channel.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channel.setFilter(text->text.matches("[0-9, ]*"));channel.setValue(channelText);channel.setResponder(text->{channelText=text;channel.setTextColor(ChannelList.parse(text)==null?0xFF7777:0xE0E0E0);if(ChannelList.parse(text)!=null)send();});addRenderableWidget(channel);
            var entries=com.vandorlabs.neoforge.client.NativeMaterialPicker.generalEntries();int selected=state.tab==0?surface>=0?surface:surface==com.vandorlabs.redstone.RedstoneScreenPolicy.REDSTONE_SURFACE?REDSTONE_ROW:artworkChoice():housing;
            if(state.tab==0)for(int i=0;i<ScreenArtwork.ENTRIES.size();i++){
                var entry=ScreenArtwork.ENTRIES.get(i);String id="screen_"+entry.texture(state.framed)+"_static";
                var finish=ModernFinishCatalog.ENTRIES.stream().filter(value->value.id().equals(id)).findFirst().orElseThrow(()->new IllegalStateException("Missing authored screen thumbnail "+id));
                entries.add(new com.vandorlabs.neoforge.client.NativeMaterialPicker.Entry(ARTWORK_BASE+i,entry.label(),"Screens",finish.texture(),finish.u(),finish.v()));
            }
            var previous=centered?picker:null;picker=new com.vandorlabs.neoforge.client.NativeMaterialPicker(panel.listX,panel.top+54,panel.listWidth,panel.rows(true),selected,entries,this::pick);
            if(state.tab==0)picker.special(REDSTONE_ROW,"Redstone...",()->{surface=com.vandorlabs.redstone.RedstoneScreenPolicy.REDSTONE_SURFACE;send();});
            picker.keepView(previous);centered=true;
        }
        private int artworkChoice(){String id=ScreenArtwork.find(artwork).id();for(int i=0;i<ScreenArtwork.ENTRIES.size();i++)if(ScreenArtwork.ENTRIES.get(i).id().equals(id))return ARTWORK_BASE+i;return ARTWORK_BASE;}
        private void pick(int choice){
            if(choice>=ARTWORK_BASE&&choice<ARTWORK_BASE+ScreenArtwork.ENTRIES.size()){var entry=ScreenArtwork.ENTRIES.get(choice-ARTWORK_BASE);artwork=entry.id();surface=-1;state=new ScreenSelectorDialog.State(false,state.tab,state.mode,state.speed,state.trigger,state.sides,state.framed,entry.paired());}
            else if(state.tab==0)surface=choice;else housing=choice;send();rebuild();
        }
        private void send(){if(ChannelList.parse(channelText)==null)return;PacketDistributor.sendToServer(new ScreenPayload(menu.containerId,menu.pos,artwork,state.mode,state.speed,state.trigger,state.sides,state.framed,channelText,housing,surface));}
        private void action(int id,int direction){if(id==20){if(ChannelList.parse(channelText)!=null){send();onClose();}return;}state=state.action(id,direction);if(id<90)send();else centered=false;rebuild();}
        @Override public boolean mouseClicked(double x,double y,int button){
            if(button==1)for(var control:controls)if(control.enabled&&(control.bounds.id==0||control.bounds.id==4||control.bounds.id==32)&&control.bounds.contains(x,y)){action(control.bounds.id,-1);return true;}
            return picker.click(x,y,button)||super.mouseClicked(x,y,button);
        }
        @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker.drag(y)||super.mouseDragged(x,y,button,dx,dy);}
        @Override public boolean mouseReleased(double x,double y,int button){picker.release();return super.mouseReleased(x,y,button);}
        @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
        @Override public void renderBackground(GuiGraphics graphics,int x,int y,float delta){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,delta,x,y);}
        @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){
            graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF19232C);graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+24,0xFF304858);picker.draw(graphics,font,x,y);
            graphics.drawString(font,title,panel.left+12,panel.top+8,0xFFFFFF,false);graphics.drawString(font,Component.translatable("gui.vandorlabs.selector.display"),panel.controlsX,panel.top+30,0xDAE8F0,false);graphics.drawString(font,Component.translatable("gui.vandorlabs.selector.speed"),panel.controlsX,panel.top+64,0xDAE8F0,false);graphics.drawString(font,"Channels (0 = none)",panel.controlsX,panel.top+166,0xDAE8F0,false);
        }
        @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
        @Override public boolean isPauseScreen(){return false;}
    }
}
