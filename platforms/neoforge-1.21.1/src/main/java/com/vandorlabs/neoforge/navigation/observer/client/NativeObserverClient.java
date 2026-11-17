package com.vandorlabs.neoforge.navigation.observer.client;
import com.vandorlabs.neoforge.navigation.observer.*;import net.minecraft.client.resources.model.*;import net.minecraft.resources.ResourceLocation;import net.neoforged.api.distmarker.Dist;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.client.event.*;
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeObserverClient {
 @SubscribeEvent public static void sprites(RegisterSpriteSourceTypesEvent e){e.register(ResourceLocation.parse("vandorlabs:observer_sprites"),NativeObserverSpriteSource.TYPE);}
 @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){for(var id:NativeObserverRenderer.MODELS)e.register(ModelResourceLocation.standalone(NativeObserverRenderer.model(id)));}
 @SubscribeEvent public static void completed(ModelEvent.BakingCompleted e){NativeObserverRenderer.bake(e.getModels());}
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(NativeObserverBindings.ENTITY,NativeObserverRenderer::new);}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){e.register(NativeObserverBindings.MENU,NativeObserverScreen::new);}
 @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT) public static final class Lifecycle {@SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload e){if(e.getLevel().isClientSide())NativeObserverRenderer.clearClock();}}
 private NativeObserverClient(){}
}
