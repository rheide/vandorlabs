package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativeCredentialReaderScreen;
import com.vandorlabs.neoforge.credentials.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Canonical reader bindings consume only the proven mounted models and public contents projection. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeReaderClientBindings {
 public static boolean installed(){return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vandorlabs:access_card_slot"))&&BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vandorlabs:data_chip_slot"));}
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(installed())event.registerBlockEntityRenderer(NativeReaderRegistrations.ENTITY.get(),NativeCredentialReaderRenderer::new);}
 @SubscribeEvent public static void menus(RegisterMenuScreensEvent event){if(installed())event.<NativeCredentialReaderMenu,NativeCredentialReaderScreen>register(NativeReaderRegistrations.MENU.get(),NativeCredentialReaderScreen::new);}
 static void prepare(java.util.Map<net.minecraft.client.resources.model.ModelResourceLocation,net.minecraft.client.resources.model.BakedModel> models){if(installed())NativeCredentialReaderModels.prepare(models,java.util.List.of(NativeReaderRegistrations.ACCESS_CARD_SLOT.get(),NativeReaderRegistrations.DATA_CHIP_SLOT.get()));}
 private NativeReaderClientBindings(){}
}
