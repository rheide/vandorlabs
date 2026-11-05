package com.vandorlabs.neoforge.fluid.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
/** Publish only after the new model/atlas generation has become active. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeFluidReload {
 private NativeFluidReload(){}
 @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event){NativeFluidClient.reload();}
}
