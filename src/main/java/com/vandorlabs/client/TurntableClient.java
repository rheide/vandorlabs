package com.vandorlabs.client;

import com.vandorlabs.network.MessageTurntablePayload;
import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.turntable.TurntableSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/** Reassembles fragmented payload snapshots and hands them to the controller tile. */
public final class TurntableClient {
    private static final Map<BlockPos, Accumulator> PENDING = new HashMap<>();

    private TurntableClient() { }

    public static void receive(MessageTurntablePayload message) {
        Minecraft.getMinecraft().addScheduledTask(() -> apply(message));
    }

    private static void apply(MessageTurntablePayload message) {
        if (message.total <= 0) {
            PENDING.remove(message.pos);
            store(message.pos, null);
            return;
        }
        Accumulator accumulator = PENDING.get(message.pos);
        if (accumulator == null || accumulator.total != message.total) {
            accumulator = new Accumulator(message.total);
            PENDING.put(message.pos, accumulator);
        }
        if (message.offset < 0 || message.offset + message.data.length > accumulator.total
                || message.data.length > TurntableSnapshot.MAX_BYTES) {
            PENDING.remove(message.pos);
            return;
        }
        System.arraycopy(message.data, 0, accumulator.bytes, message.offset, message.data.length);
        accumulator.received += message.data.length;
        if (accumulator.received < accumulator.total) return;
        PENDING.remove(message.pos);
        try {
            store(message.pos, TurntableSnapshot.decode(accumulator.bytes));
        } catch (Exception malformed) {
            store(message.pos, null);
        }
    }

    private static void store(BlockPos pos, TurntableSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) return;
        TileEntity tile = minecraft.world.getTileEntity(pos);
        if (!(tile instanceof TileEntityTurntableController)) return;
        ((TileEntityTurntableController) tile).setClientPayload(snapshot);
        minecraft.world.markBlockRangeForRenderUpdate(pos.add(-24, -24, -24), pos.add(24, 24, 24));
    }

    private static final class Accumulator {
        final int total;
        final byte[] bytes;
        int received;

        Accumulator(int total) { this.total = total; this.bytes = new byte[total]; }
    }
}
