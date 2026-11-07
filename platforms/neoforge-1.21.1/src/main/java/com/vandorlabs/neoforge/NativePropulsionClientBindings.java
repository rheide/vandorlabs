package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativePropulsionScreen;
import com.vandorlabs.neoforge.propulsion.*;
import com.vandorlabs.propulsion.PropulsionDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativePropulsionClientBindings {
 static boolean engine(ResourceLocation id){if(!id.getNamespace().equals("vandorlabs"))return false;for(var definition:PropulsionDefinition.values())if(definition.id.equals(id.getPath()))return true;return false;}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vandorlabs:rocket_thruster")))event.<NativePropulsionMenu,NativePropulsionScreen>register(NativePropulsionRegistrations.MENU.get(),NativePropulsionScreen::new);}
 private NativePropulsionClientBindings(){}
}
