package com.vandorlabs.neoforge.compat.jei;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
/** Keep optional JEI types out of ordinary native test startup linkage. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeMachineJeiEvents {
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(NativeMachineJeiFixture.enabled())NativeMachineJeiChecks.tick(event);}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{if(NativeMachineJeiFixture.enabled())NativeMachineJeiChecks.frame(event);}
 private NativeMachineJeiEvents(){}
}
