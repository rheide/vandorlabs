package com.vandorlabs.neoforge.vehicle;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Keep the descent key from triggering vanilla sneak-dismount while in an airborne mode. */
@EventBusSubscriber(modid = "vandorlabs")
final class NativeVehicleRiders {
    private static final Map<ServerPlayer, Long> RESTORE = new IdentityHashMap<>();

    @SubscribeEvent
    public static void staySeated(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityMounting() instanceof Player player
                && player.isAlive() && !player.isRemoved() && player.isShiftKeyDown()
                && event.getEntityBeingMounted() instanceof NativeGroundVehicle craft
                && craft.isAlive() && craft.mode() != NativeGroundVehicle.TAXI) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void resumeSeat(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isPassenger()
                || player.getY() < NativeVehicleCruise.SKY_OFFSET) return;
        RESTORE.put(player, player.serverLevel().getGameTime() + 80);
    }

    @SubscribeEvent
    public static void disconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RESTORE.remove(player);
    }

    @SubscribeEvent
    public static void restoreSeat(ServerTickEvent.Post event) {
        var waiting = RESTORE.entrySet().iterator();
        while (waiting.hasNext()) {
            var attempt = waiting.next();
            var player = attempt.getKey();
            var world = player.serverLevel();
            if (world.getServer() != event.getServer()) continue;
            if (player.isPassenger() || player.isRemoved() || world.getGameTime() >= attempt.getValue()) {
                waiting.remove();
                continue;
            }
            if (world.getGameTime() % 4 != 0) continue;
            var search = player.getBoundingBox().inflate(40, 40, 40);
            for (var craft : world.getEntitiesOfClass(NativeGroundVehicle.class, search)) {
                if (!craft.resumeFlightSeat(player)) continue;
                craft.positionRider(player);
                waiting.remove();
                break;
            }
        }
    }

    private NativeVehicleRiders() {}
}
