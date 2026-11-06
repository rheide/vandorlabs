package com.vandorlabs.neoforge.light.client;
import com.vandorlabs.neoforge.light.NativeLightRenderService;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
/** Coalesce incoming member packets, then publish complete immutable pairs before drawing. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeLightProjectionClient {
 private NativeLightProjectionClient(){}
 @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){var world=Minecraft.getInstance().level;if(world!=null)NativeLightRenderService.flush(world);}
}
