package com.vandorlabs.network;

import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** One entity update per viewer, including riders whose tracker has not caught up yet. */
public final class TrackingPackets {
    private static final BiConsumer<IMessage, Entity> SEND_PASSENGER = (message, passenger) -> {
        if (passenger instanceof EntityPlayerMP) {
            PacketHandler.INSTANCE.sendTo(message, (EntityPlayerMP) passenger);
        }
    };

    /** Call on the authoritative server thread, just like sendToAllTracking. */
    public static void send(IMessage message, Entity entity) {
        if (!(entity.world instanceof WorldServer)) return;
        Set<? extends EntityPlayer> tracking = ((WorldServer) entity.world)
                .getEntityTracker().getTrackingPlayers(entity);
        // Forge's entity broadcast already includes tracked riders. Sending the
        // driver again repeats serialization, delivery and client decoding.
        PacketHandler.INSTANCE.sendToAllTracking(message, entity);
        sendMissing(message, tracking, entity.getPassengers(), SEND_PASSENGER);
    }

    /** Keep the fallback independent of the live tracker so watcher transitions are testable. */
    static <M, P> void sendMissing(M message, Set<?> tracking, Iterable<P> passengers,
            BiConsumer<? super M, ? super P> send) {
        for (P passenger : passengers) {
            if (!tracking.contains(passenger)) send.accept(message, passenger);
        }
    }

    private TrackingPackets() { }
}
