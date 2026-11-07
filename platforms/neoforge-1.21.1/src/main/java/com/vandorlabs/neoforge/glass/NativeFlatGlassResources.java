package com.vandorlabs.neoforge.glass;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Authored additional models are dormant until an explicit private focus or the canonical block exists. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeFlatGlassResources {
 public static boolean active(){return "true".equals(System.getenv("VANDOR_FLAT_GLASS_RENDER_CHECK"))||"true".equals(System.getenv("VANDOR_FLAT_GLASS_WORLD_CHECK"))||net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vandorlabs:programmable_glass"));}
 @SubscribeEvent public static void additional(ModelEvent.RegisterAdditional event){if(active())for(String id:NativeFlatGlassAssets.models())event.register(ModelResourceLocation.standalone(ResourceLocation.parse(id)));}
 private NativeFlatGlassResources(){}
}
