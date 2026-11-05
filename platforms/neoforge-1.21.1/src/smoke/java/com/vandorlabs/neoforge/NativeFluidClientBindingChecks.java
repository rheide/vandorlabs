package com.vandorlabs.neoforge;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
/** Title-screen class binding only; does not claim fluid gameplay or rendered-world acceptance. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeFluidClientBindingChecks {
 private static int ticks;
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!"true".equals(System.getenv("VANDOR_FLUID_BINDING")))return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(++ticks<100||mc.getOverlay()!=null)return;
  try{for(String[] target:new String[][]{{"net.minecraft.client.Camera","containedCamera"},{"net.minecraft.client.renderer.block.LiquidBlockRenderer","vandorlabs$render"},{"net.minecraft.world.entity.Entity","containedBody"},{"net.minecraft.client.multiplayer.ClientLevel","fluidNeighborChanged"}}){Class<?> type=Class.forName(target[0]);if(java.util.Arrays.stream(type.getDeclaredMethods()).noneMatch(method->method.getName().contains(target[1])))throw new IllegalStateException("Fluid mixin not bound: "+target[0]+"/"+target[1]);}}
  catch(ClassNotFoundException failure){throw new IllegalStateException(failure);}System.out.println("[vandorlabs][fluid-bind] client startup PASS camera/liquid/entity target classes; title screen only; client-level binding");mc.stop();
 }
}
