package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.*;
import com.vandorlabs.neoforge.glass.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Bind only installed canonical ordinary Glass; diagonal Glass retains its existing panel path. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeGlassClientBindings {
 private static final ResourceLocation ID=ResourceLocation.parse("vandorlabs:programmable_glass");
 public static boolean glass(ResourceLocation id){return ID.equals(id);}
 public static boolean installed(){return BuiltInRegistries.BLOCK.get(ID) instanceof NativeGlassBlock;}
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){if(installed())e.registerBlockEntityRenderer(NativeGlassRegistrations.ENTITY.get(),NativeGlassBlockEntityRenderer::new);}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){if(installed())e.<NativeGlassMenu,NativeGlassScreen>register(NativeGlassRegistrations.MENU.get(),NativeGlassScreen::new);}
 private NativeGlassClientBindings(){}
}
