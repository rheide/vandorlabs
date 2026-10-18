package com.vandorlabs.turntable;

import com.vandorlabs.network.MessageTurntableController;
import com.vandorlabs.network.MessageTurntablePayload;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityTurntableController;
import net.minecraft.entity.player.EntityPlayerMP;

import java.io.IOException;
import java.util.Arrays;

/** Delivers payload snapshots to nearby clients and asks the server for them again. */
public final class TurntableNetwork {
    public static final int FRAGMENT = 28000;
    public static final int RANGE = 160;

    private TurntableNetwork() { }

    /** Ask the server to resend the payload, used when a client sees a running turntable. */
    public static void request(net.minecraft.util.math.BlockPos pos) {
        PacketHandler.INSTANCE.sendToServer(MessageTurntableController.requestPayload(pos));
    }

    public static void broadcast(TileEntityTurntableController controller) {
        if (controller.getWorld() == null || controller.getWorld().isRemote) return;
        if (!controller.isActive() || !controller.isCaptured()) return;
        for (EntityPlayerMP player : controller.getWorld().getEntitiesWithinAABB(EntityPlayerMP.class,
                new net.minecraft.util.math.AxisAlignedBB(controller.getPos()).grow(RANGE)))
            send(player, controller);
    }

    public static void send(EntityPlayerMP player, TileEntityTurntableController controller) {
        TurntableSnapshot snapshot = controller.captureSnapshot();
        byte[] bytes;
        try {
            bytes = snapshot == null ? new byte[0] : snapshot.encode();
        } catch (IOException failure) {
            return;
        }
        for (int offset = 0; offset < bytes.length; offset += FRAGMENT) {
            byte[] chunk = Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + FRAGMENT));
            PacketHandler.INSTANCE.sendTo(new MessageTurntablePayload(controller.getPos(), bytes.length, offset, chunk),
                    player);
        }
        if (bytes.length == 0)
            PacketHandler.INSTANCE.sendTo(new MessageTurntablePayload(controller.getPos(), 0, 0, new byte[0]), player);
    }
}
