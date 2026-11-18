package com.vandorlabs.neoforge.copier.client;

import com.vandorlabs.neoforge.copier.NativeCopyBindings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeCopyClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(NativeCopyBindings.MENU,NativeCopyScreen::new);}
    private NativeCopyClient(){}
}
