package com.vandorlabs.neoforge.gear.track.client;

import com.vandorlabs.neoforge.gear.track.*;
import com.vandorlabs.ui.DialogInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Standard Track style option; attached arms open their normal mechanism/redstone tabs. */
public final class NativeTrackScreen extends AbstractContainerScreen<NativeTrackMenu> {
    private Button style;
    public NativeTrackScreen(NativeTrackMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @Override protected void init(){imageWidth=Math.min(380,width-12);imageHeight=180;super.init();style=addRenderableWidget(Button.builder(Component.literal(label()),ignored->cycle(1)).bounds(leftPos+12,topPos+56,imageWidth-24,20).build());addRenderableWidget(Button.builder(Component.translatable("gui.done"),ignored->onClose()).bounds(leftPos+(imageWidth-200)/2,topPos+imageHeight-34,200,20).build());}
    private String label(){return "Style: "+new String[]{"Compact","Armoured","Bogie"}[menu.tile.style().ordinal()];}
    private void cycle(int direction){int next=DialogInput.cycle(menu.tile.style().ordinal(),3,direction);net.neoforged.neoforge.network.PacketDistributor.sendToServer(new NativeTrackStylePayload(menu.containerId,menu.selected,menu.identity,next));}
    @Override protected void containerTick(){super.containerTick();style.setMessage(Component.literal(label()));}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1&&style.isMouseOver(x,y)){cycle(-1);return true;}return super.mouseClicked(x,y,button);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff19232c);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+24,0xff304858);g.drawString(font,title,leftPos+12,topPos+8,0xffffff,false);if(!menu.tile.error().isEmpty())g.drawWordWrap(font,Component.literal(menu.tile.error()),leftPos+12,topPos+112,imageWidth-24,0xff8888);g.drawWordWrap(font,Component.literal("Mount on an arm to configure movement and Redstone activation."),leftPos+12,topPos+88,imageWidth-24,0xcdd7e4);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xc0101010,0xd0101010);renderBg(g,partial,x,y);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
