package com.vandorlabs.neoforge.control;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.MountedControlDialog;
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
public final class ControlScreen extends AbstractContainerScreen<ControlMenu> {
    private MountedControlDialog layout;
    private MountedControlDialog.State state;
    private EditBox channels;
    public ControlScreen(ControlMenu menu,Inventory inventory,Component title){super(menu,inventory,Component.literal("Redstone Channels"));state=new MountedControlDialog.State(menu.tile.baseHeight(),menu.tile.baseTilt(),menu.tile.tiltDirection());imageWidth=240;imageHeight=168;}
    @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event){event.register(ControlMenu.TYPE.get(),ControlScreen::new);}
    @Override protected void init(){String draft=channels==null?menu.tile.getRedstoneChannels().toString():channels.getValue();super.init();clearWidgets();layout=new MountedControlDialog(width,height);var b=layout.channels();channels=new EditBox(font,b.x,b.y,b.width,b.height,Component.literal("Channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setFilter(text->text.matches("[0-9, ]*"));channels.setValue(draft);addRenderableWidget(channels);setInitialFocus(channels);buttons();}
    private void buttons(){for(var b:layout.controls(state))addRenderableWidget(Button.builder(Component.literal(b.label),ignored->action(b.id,1)).bounds(b.x,b.y,b.width,b.height).build());}
    private void action(int id,int direction){if(id==1){submit();return;}state=state.action(id,direction);String draft=channels.getValue();clearWidgets();addRenderableWidget(channels);channels.setValue(draft);buttons();}
    private void submit(){ChannelList parsed=ChannelList.parse(channels.getValue());channels.setTextColor(parsed==null?0xFF7777:0xE0E0E0);if(parsed!=null){PacketDistributor.sendToServer(new ControlPayload(menu.containerId,menu.pos,parsed.toString(),state.height,state.tilt,state.direction));onClose();}}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1)for(var b:layout.controls(state))if(b.id!=1&&b.contains(x,y)){action(b.id,-1);return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257||key==335){submit();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float delta){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,delta,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(layout.left,layout.top,layout.left+240,layout.top+168,0xFF101012);g.fill(layout.left,layout.top,layout.left+240,layout.top+24,0xFF304858);g.drawString(font,title,layout.left+12,layout.top+8,0xFFFFFF,false);g.drawString(font,"Channels",layout.left+14,layout.top+43,0xDAE8F0,false);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
