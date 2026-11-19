package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.ramp.NativeRampMotionPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Adds a bounded heading change to the native support position and sequence. */
record NativeTurntableRiderPayload(NativeRampMotionPayload support, float yaw) implements CustomPacketPayload {
    private static final Type<NativeTurntableRiderPayload> TYPE =
            new Type<>(ResourceLocation.parse("vandorlabs:turntable_rider"));
    static final StreamCodec<RegistryFriendlyByteBuf, NativeTurntableRiderPayload> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeFloat(packet.yaw);
                NativeRampMotionPayload.CODEC.encode(buffer, packet.support);
            }, buffer -> {
                float yaw = buffer.readFloat();
                if (!Float.isFinite(yaw) || Math.abs(yaw) > 3) throw new IllegalArgumentException("Invalid Turntable yaw");
                return new NativeTurntableRiderPayload(NativeRampMotionPayload.CODEC.decode(buffer), yaw);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            var player = context.player();
            if (payload.support.apply(player)) {
                player.setYRot(player.getYRot() + payload.yaw);
                player.yRotO += payload.yaw;
            }
        }));
    }
}
