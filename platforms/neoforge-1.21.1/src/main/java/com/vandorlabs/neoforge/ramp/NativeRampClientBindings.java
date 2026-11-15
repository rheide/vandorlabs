package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.client.NativeRampPartBlockRenderer;
import com.vandorlabs.neoforge.client.NativeRampScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeRampClientBindings {
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){if(NativeRampBindings.enabled())event.enqueueWork(()->NativeRampBindings.bindMotion(com.vandorlabs.neoforge.client.NativeRampRiderClient::receive));}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(NativeRampBindings.enabled())NativeRampScreen.register(event,NativeRampBindings.MENU);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(NativeRampBindings.enabled())event.registerBlockEntityRenderer(NativeRampBindings.PART_ENTITY,NativeRampPartBlockRenderer::new);}
    private NativeRampClientBindings(){}
}
