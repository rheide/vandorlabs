package com.vandorlabs.neoforge.builders;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The Configurizer uses the same authenticated menu path as ordinary machine clicks. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeBuilderConfiguration {
    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickBlock event){
        if(event.getHand()!=InteractionHand.MAIN_HAND
                || !event.getItemStack().is(VandorLabsBootstrap.CONFIGURIZER.get())
                || !event.getLevel().hasChunkAt(event.getPos()))return;
        var owner=NativeBuilderBindings.ownerAt(event.getLevel(),event.getPos());
        if(owner==null||!owner.formed())return;
        if(NativeBuilderBindings.open(event.getEntity(),event.getLevel(),event.getPos())){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }
    private NativeBuilderConfiguration(){}
}
