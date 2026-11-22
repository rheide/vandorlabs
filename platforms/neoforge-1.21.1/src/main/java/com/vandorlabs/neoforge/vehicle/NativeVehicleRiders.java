package com.vandorlabs.neoforge.vehicle;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/** Keep the descent key from triggering vanilla sneak-dismount while in an airborne mode. */
@EventBusSubscriber(modid = "vandorlabs")
final class NativeVehicleRiders {
    @SubscribeEvent
    public static void staySeated(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityMounting() instanceof Player player
                && player.isAlive() && !player.isRemoved() && player.isShiftKeyDown()
                && event.getEntityBeingMounted() instanceof NativeGroundVehicle craft
                && craft.isAlive() && craft.mode() != NativeGroundVehicle.TAXI) event.setCanceled(true);
    }

    private NativeVehicleRiders() {}
}
