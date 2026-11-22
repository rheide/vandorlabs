package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.client.NativeDialogButton;
import com.vandorlabs.neoforge.vehicle.NativeVehicleBindings;
import com.vandorlabs.neoforge.vehicle.NativeVehicleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeVehicleControllerScreen extends AbstractContainerScreen<NativeVehicleMenu> {
    public NativeVehicleControllerScreen(NativeVehicleMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);
        imageWidth=218; imageHeight=88;
        titleLabelX=12; titleLabelY=10;
    }

    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(NativeVehicleBindings.MENU,NativeVehicleControllerScreen::new);
    }

    @Override protected void init() {
        super.init();
        var caption=Component.literal(menu.controller!=null?"Assemble in Taxi Mode":"Park as blocks");
        addRenderableWidget(NativeDialogButton.builder(caption, button -> {
            if(minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);
        }).bounds(leftPos+12,topPos+52,imageWidth-24,20).build());
    }

    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY) {
        graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff27343d);
        graphics.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xff18232b);
    }

    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {
        graphics.drawString(font,title,titleLabelX,titleLabelY,0xffe7eff4,false);
        if(menu.controller!=null) graphics.drawString(font,"Mode: Park",12,31,0xffafcad6,false);
        else if(menu.craft!=null && menu.craft.snapshot()!=null)
            graphics.drawString(font,"Carried blocks: "+menu.craft.snapshot().cells().size(),12,31,0xffafcad6,false);
    }
}
