package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.storage.*;
import com.vandorlabs.neoforge.client.NativeStorageScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
/** Conditional canonical static-chunk/item and dedicated editor binding. Storage has no BER. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeStorageClientBindings {
 private static final ResourceLocation ID=ResourceLocation.parse("vandorlabs:programmable_storage");
 public static boolean storage(ResourceLocation id){return ID.equals(id);}
 public static boolean installed(){return BuiltInRegistries.BLOCK.get(ID) instanceof NativeStorageBlock;}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(installed())NativeStorageScreen.register(event,NativeStorageRegistrations.MENU.get());}
 private NativeStorageClientBindings(){}
}
