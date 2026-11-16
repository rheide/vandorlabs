package com.vandorlabs.neoforge.credentials;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeChipTypeClient {
 @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(NativeCredentialItems.DATA_CHIP.get(),net.minecraft.resources.ResourceLocation.parse("vandorlabs:chip_type"),(stack,world,entity,seed)->NativeChipTypes.appearance(stack)));}
 private NativeChipTypeClient(){}
}
