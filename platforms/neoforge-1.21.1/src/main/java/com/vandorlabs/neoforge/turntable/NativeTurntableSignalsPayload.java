package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.turntable.TurntableSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Applies one complete redstone settings draft to the authenticated controller menu. */
record NativeTurntableSignalsPayload(int window, int scanMode, int rotationMode, int snapSteps,
                                    boolean clockwise, ChannelList scan, ChannelList rotation,
                                    ChannelList direction, ChannelList restore) implements CustomPacketPayload {
    private static final Type<NativeTurntableSignalsPayload> TYPE =
            new Type<>(ResourceLocation.parse("vandorlabs:turntable_signals"));
    static final StreamCodec<RegistryFriendlyByteBuf, NativeTurntableSignalsPayload> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.window);
                buffer.writeByte(packet.scanMode);
                buffer.writeByte(packet.rotationMode);
                buffer.writeByte(packet.snapSteps);
                buffer.writeBoolean(packet.clockwise);
                writeBank(buffer, packet.scan);
                writeBank(buffer, packet.rotation);
                writeBank(buffer, packet.direction);
                writeBank(buffer, packet.restore);
            }, buffer -> {
                if (buffer.readableBytes() > 13 + 4 * Integer.BYTES * ChannelList.MAX_CHANNELS) {
                    throw new IllegalArgumentException("Turntable settings packet too large");
                }
                var packet = new NativeTurntableSignalsPayload(buffer.readVarInt(), buffer.readUnsignedByte(),
                        buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readBoolean(), readBank(buffer),
                        readBank(buffer), readBank(buffer), readBank(buffer));
                if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected Turntable settings bytes");
                return packet;
            });

    NativeTurntableSignalsPayload {
        if (scanMode < TurntableSettings.SCAN_DISABLED || scanMode > TurntableSettings.SIGNAL_OFF
                || rotationMode < TurntableSettings.SIGNAL_ON || rotationMode > TurntableSettings.SIGNAL_OFF
                || snapSteps < 0 || snapSteps > 50) throw new IllegalArgumentException("Invalid Turntable signals");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer actor && actor.containerMenu instanceof NativeTurntableMenu menu
                    && menu.containerId == payload.window) menu.configureSignals(actor, payload);
        }));
    }

    private static void writeBank(RegistryFriendlyByteBuf buffer, ChannelList bank) {
        buffer.writeByte(bank.size());
        for (int i = 0; i < bank.size(); i++) buffer.writeInt(bank.get(i));
    }

    private static ChannelList readBank(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readUnsignedByte();
        if (size > ChannelList.MAX_CHANNELS || buffer.readableBytes() < size * Integer.BYTES) {
            throw new IllegalArgumentException("Invalid Turntable channel bank");
        }
        int[] channels = new int[size];
        for (int i = 0; i < size; i++) channels[i] = buffer.readInt();
        return ChannelList.of(channels);
    }
}
