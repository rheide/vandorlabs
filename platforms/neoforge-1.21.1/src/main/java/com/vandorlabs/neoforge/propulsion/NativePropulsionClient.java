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
 @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){var level=Minecraft.getInstance().level;if(level!=null&&NativePropulsionService.hasPending(level))NativePropulsionService.flush(level);}
}
