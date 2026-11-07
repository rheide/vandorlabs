package com.vandorlabs.neoforge.propulsion;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
/** Coalesces loaded client packet/shape bursts before terrain workers consume the cached publication. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativePropulsionClient {
 private NativePropulsionClient(){}
 private static void flush(){var level=Minecraft.getInstance().level;if(level!=null&&NativePropulsionService.hasPending(level))NativePropulsionService.flush(level);}
 @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){flush();}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event){flush();}
 @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())NativePropulsionParticles.clear();}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Setup{@SubscribeEvent public static void client(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(()->NativePropulsionTicks.installClient(NativePropulsionParticles::tick));}}

}
