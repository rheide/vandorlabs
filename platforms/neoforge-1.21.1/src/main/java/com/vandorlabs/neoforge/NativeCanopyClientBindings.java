package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.canopy.*;
import com.vandorlabs.neoforge.client.NativeCanopyScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Conditional canonical editor binding; inactive definitions have no registry-loading side effect. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeCanopyClientBindings {
    private static final ResourceLocation TYPE=ResourceLocation.fromNamespaceAndPath("vandorlabs","canopy");
    public static boolean installed(){return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs","canopy")) instanceof NativeCanopyBlock&&BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(TYPE)&&BuiltInRegistries.MENU.containsKey(TYPE);}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(installed())NativeCanopyScreen.register(event,NativeCanopyRegistrations.MENU.get());}
    private NativeCanopyClientBindings(){}
}
