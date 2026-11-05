package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Same held-item channel-only editor and synchronized-preview boundary as Forge. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TransmitterScreen extends AbstractContainerScreen<TransmitterMenu> {
    private EditBox channels;private Button done;private boolean loaded;
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(VandorLabsBootstrap.TRANSMITTER_MENU.get(),TransmitterScreen::new);}
    public TransmitterScreen(TransmitterMenu menu,net.minecraft.world.entity.player.Inventory inventory,Component title){super(menu,inventory,Component.literal("Transmitter Channels"));imageWidth=280;imageHeight=100;}
    @Override protected void init(){super.init();channels=new EditBox(font,leftPos+12,topPos+36,imageWidth-24,18,Component.literal("Channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setFilter(text->text.matches("[0-9, ]*"));channels.setValue(NativeTransmitterItem.channels(menu.editorStack()).toString());channels.setEditable(false);addRenderableWidget(channels);done=addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->save()).bounds(leftPos+12,topPos+66,imageWidth-24,20).build());done.active=false;setInitialFocus(channels);loaded=false;}
    private void save(){if(!loaded||ChannelList.parse(channels.getValue())==null)return;PacketDistributor.sendToServer(new TransmitterPayload(menu.containerId,channels.getValue()));minecraft.player.closeContainer();}
    @Override protected void containerTick(){super.containerTick();if(!loaded&&menu.synchronizedStack){channels.setValue(NativeTransmitterItem.channels(menu.editorStack()).toString());channels.setEditable(true);loaded=true;done.active=true;}}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257||key==335){save();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff101012);graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+24,0xff304858);}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){super.render(graphics,x,y,partial);renderTooltip(graphics,x,y);}
}
