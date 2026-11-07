package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.canopy.*;
import com.vandorlabs.neoforge.client.NativeCanopyScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Conditional canonical client binding; inactive definitions have no registry-loading side effect. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeCanopyClientBindings {
    private static final ResourceLocation TYPE=ResourceLocation.fromNamespaceAndPath("vandorlabs","canopy");
    public static boolean installed(){return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs","canopy")) instanceof NativeCanopyBlock&&BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(TYPE)&&BuiltInRegistries.MENU.containsKey(TYPE);}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(installed())NativeCanopyScreen.register(event,NativeCanopyRegistrations.MENU.get());}
    @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event){if(installed())event.registerBlockEntityRenderer(NativeCanopyRegistrations.ENTITY.get(),com.vandorlabs.neoforge.client.NativeCanopyBlockRenderer::new);}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOW) public static void terrain(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event){
        if(!installed())return;var regular=ResourceLocation.fromNamespaceAndPath("vandorlabs","regular_canopy_glass");
        event.getModels().replaceAll((id,model)->id.id().equals(regular)&&!id.getVariant().equals("inventory")?NativeCanopyRenderer.terrain(event.getModelBakery(),model):model);
    }
    private NativeCanopyClientBindings(){}
}
