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

/** Client bindings for authored Doors and the dedicated Segmented Door backend. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeDoorClientBindings {
    private NativeDoorClientBindings(){}
    static boolean ordinary(ResourceLocation id){return id.getNamespace().equals("vandorlabs")&&(id.getPath().equals(NativeDoorSettings.ORDINARY_ID)||id.getPath().equals(NativeDoorSettings.LEGACY_ROTATING_ID)||id.getPath().equals(NativeDoorSettings.LEGACY_SLIDING_ID));}
    static boolean large(ResourceLocation id){return id.getNamespace().equals("vandorlabs")&&id.getPath().equals(NativeDoorSettings.LARGE_ID);}
    static boolean segmented(ResourceLocation id){return id.getNamespace().equals("vandorlabs")&&id.getPath().equals(com.vandorlabs.neoforge.segmented.NativeSegmentedRegistrations.ID);}
    private static boolean active(){return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",NativeDoorSettings.ORDINARY_ID));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(active())event.registerBlockEntityRenderer(NativeDoorRegistrations.ENTITY.get(),ignored->new NativeDoorRenderer());if(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",NativeDoorSettings.LARGE_ID)))event.registerBlockEntityRenderer(NativeLargeDoorRegistrations.ENTITY.get(),ignored->new NativeDoorRenderer());if(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",com.vandorlabs.neoforge.segmented.NativeSegmentedRegistrations.ID))){event.registerBlockEntityRenderer(com.vandorlabs.neoforge.segmented.NativeSegmentedRegistrations.ENTITY.get(),NativeSegmentedBlockRenderer::new);com.vandorlabs.neoforge.segmented.NativeSegmentedTargets.clientPose(NativeSegmentedBlockRenderer::selectionPose);}}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(active())event.<NativeDoorMenu,NativeDoorScreen>register(NativeDoorRegistrations.MENU.get(),(menu,inventory,title)->new NativeDoorScreen(menu,inventory,title,new NativeCredentialFilterPicker(NativeCredentialItems::accessCard,false)));if(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",com.vandorlabs.neoforge.segmented.NativeSegmentedRegistrations.ID)))event.<com.vandorlabs.neoforge.segmented.NativeSegmentedMenu,NativeSegmentedScreen>register(com.vandorlabs.neoforge.segmented.NativeSegmentedRegistrations.MENU.get(),NativeSegmentedScreen::new);}
}
