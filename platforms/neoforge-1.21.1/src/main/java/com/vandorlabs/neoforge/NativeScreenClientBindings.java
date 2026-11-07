package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativeScreenScreen;
import com.vandorlabs.neoforge.screen.NativeScreenRegistrations;
import com.vandorlabs.neoforge.screen.NativeScreenMenu;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Canonical Screen menu, shader and authored inventory bindings. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeScreenClientBindings {
 private NativeScreenClientBindings(){}
 public static boolean installed(){return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vandorlabs:programmable_viewscreen"));}
 static ScreenFamily family(ResourceLocation id){if(!id.getNamespace().equals("vandorlabs"))return null;for(var family:ScreenFamily.values())if(family.id.equals(id.getPath()))return family;return null;}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(installed())event.<NativeScreenMenu,NativeScreenScreen>register(NativeScreenRegistrations.MENU.get(),NativeScreenScreen::new);}
 @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws java.io.IOException{if(installed())NativeScreenLayers.registerShaders(event);}
 @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT) public static final class Lifetime {
  @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof net.minecraft.world.level.Level level&&level.isClientSide){com.vandorlabs.neoforge.screen.NativeScreenRenderGroups.clear(level);com.vandorlabs.neoforge.screen.NativeScreenRenderQueries.clear();}}
 }

}
