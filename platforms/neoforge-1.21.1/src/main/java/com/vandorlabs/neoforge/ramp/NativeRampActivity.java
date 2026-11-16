package com.vandorlabs.neoforge.ramp;

import java.util.Map;
import java.util.WeakHashMap;

/** Weak operation registry for Source-compatible one-second activity hysteresis. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
public final class NativeRampActivity {
 private static final Map<NativeRampController,Boolean> ACTIVE=new WeakHashMap<>();
 static void watch(NativeRampController controller){ACTIVE.put(controller,Boolean.TRUE);}
 static void unwatch(NativeRampController controller){ACTIVE.remove(controller);}
 @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  if(event.getServer().getTickCount()%20!=0)return;
  ACTIVE.keySet().removeIf(controller->!controller.refreshActivity(event.getServer()));
 }
 @net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)ACTIVE.keySet().removeIf(controller->controller.belongs(level));}
 private NativeRampActivity(){}
}
