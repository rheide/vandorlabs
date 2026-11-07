package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.programmer.*;
import com.vandorlabs.neoforge.client.NativeProgrammerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
/** Canonical bindings use the same authored bake, dock projection and dedicated dialog. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeProgrammerClientBindings {
 private static final ResourceLocation ID=ResourceLocation.parse("vandorlabs:data_chip_programmer");
 public static boolean programmer(ResourceLocation id){return ID.equals(id);}
 public static boolean installed(){return BuiltInRegistries.BLOCK.get(ID) instanceof NativeProgrammerBlock;}
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(installed())event.registerBlockEntityRenderer(NativeProgrammerRegistrations.ENTITY.get(),NativeProgrammerBlockRenderer::new);}
 @SubscribeEvent public static void menus(RegisterMenuScreensEvent event){if(installed())NativeProgrammerScreen.register(event,NativeProgrammerRegistrations.MENU.get());}
 @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){if(installed())NativeProgrammerScreen.registerReload(event);}
 private NativeProgrammerClientBindings(){}
}
