package com.vandorlabs.neoforge.infuser;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Both interaction paths use the same inventory and authorization checks. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeInfuserConfiguration {
    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand()!=InteractionHand.MAIN_HAND
                || !event.getItemStack().is(VandorLabsBootstrap.CONFIGURIZER.get())
                || !event.getLevel().hasChunkAt(event.getPos())) return;
        if (NativeInfuserBindings.open(event.getEntity(),event.getLevel(),event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }
    private NativeInfuserConfiguration() {}
}
