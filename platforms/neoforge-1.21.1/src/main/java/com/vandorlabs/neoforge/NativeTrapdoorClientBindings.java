package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativeTrapdoorBlockRenderer;
import com.vandorlabs.neoforge.client.NativeTrapdoorScreen;
import com.vandorlabs.neoforge.trapdoor.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Client bindings follow installed canonical registry IDs; private hosts remain independently bound. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeTrapdoorClientBindings {
    static boolean trapdoor(ResourceLocation id){return id.getNamespace().equals("vandorlabs")&&(id.getPath().equals(NativeTrapdoorRegistrations.ID)||id.getPath().equals(NativeTrapdoorRegistrations.DIAGONAL_ID));}
    private static boolean installed(String id){return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",id));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        if(installed(NativeTrapdoorRegistrations.ID))event.registerBlockEntityRenderer(NativeTrapdoorRegistrations.ENTITY.get(),NativeTrapdoorBlockRenderer::new);
        if(installed(NativeTrapdoorRegistrations.DIAGONAL_ID))event.registerBlockEntityRenderer(NativeTrapdoorRegistrations.DIAGONAL_ENTITY.get(),NativeTrapdoorBlockRenderer::new);
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){
        if(installed(NativeTrapdoorRegistrations.ID))event.<NativeTrapdoorMenu,NativeTrapdoorScreen>register(NativeTrapdoorRegistrations.MENU.get(),NativeTrapdoorScreen::new);
    }
    private NativeTrapdoorClientBindings(){}
}
