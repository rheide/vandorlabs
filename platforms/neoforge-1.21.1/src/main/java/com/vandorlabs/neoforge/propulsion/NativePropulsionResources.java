package com.vandorlabs.neoforge.propulsion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
/** Authored model projection is dormant until a real engine binding or an explicit private render fixture exists. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativePropulsionResources {
 private NativePropulsionResources(){}
 public static boolean active(){return "true".equals(System.getenv("VANDOR_PROPULSION_RENDER_CHECK"))||net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vandorlabs:rocket_thruster")) instanceof NativePropulsionBlock;}
 @SubscribeEvent public static void additional(ModelEvent.RegisterAdditional event){if(active())for(String id:NativePropulsionAssets.models())event.register(ModelResourceLocation.standalone(ResourceLocation.parse(id)));}
}
