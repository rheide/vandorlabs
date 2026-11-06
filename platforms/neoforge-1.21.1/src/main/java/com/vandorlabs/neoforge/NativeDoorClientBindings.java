package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.*;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.door.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** One renderer, item adapter and dedicated editor for the canonical Door and its retained aliases. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeDoorClientBindings {
    private NativeDoorClientBindings(){}
    static boolean ordinary(ResourceLocation id){return id.getNamespace().equals("vandorlabs")&&(id.getPath().equals(NativeDoorSettings.ORDINARY_ID)||id.getPath().equals(NativeDoorSettings.LEGACY_ROTATING_ID)||id.getPath().equals(NativeDoorSettings.LEGACY_SLIDING_ID));}
    private static boolean active(){return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",NativeDoorSettings.ORDINARY_ID));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(active())event.registerBlockEntityRenderer(NativeDoorRegistrations.ENTITY.get(),ignored->new NativeDoorRenderer());}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(active())event.<NativeDoorMenu,NativeDoorScreen>register(NativeDoorRegistrations.MENU.get(),(menu,inventory,title)->new NativeDoorScreen(menu,inventory,title,new NativeCredentialFilterPicker(NativeCredentialItems::accessCard,false)));}
}
