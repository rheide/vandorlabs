package com.vandorlabs.neoforge.turntable;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A moving owner sends pose scalars without resending captured block artwork. */
record NativeTurntableAnglePayload(BlockPos pos, UUID key, long sequence, float angle) implements CustomPacketPayload {
    private static final Type<NativeTurntableAnglePayload> TYPE =
            new Type<>(ResourceLocation.parse("vandorlabs:turntable_angle"));
    static final StreamCodec<RegistryFriendlyByteBuf, NativeTurntableAnglePayload> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBlockPos(packet.pos);
                buffer.writeUUID(packet.key);
                buffer.writeVarLong(packet.sequence);
                buffer.writeFloat(packet.angle);
            }, buffer -> new NativeTurntableAnglePayload(buffer.readBlockPos(), buffer.readUUID(),
                    buffer.readVarLong(), buffer.readFloat()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            var world = context.player().level();
            if (world.isClientSide && world.hasChunkAt(payload.pos)
                    && world.getBlockEntity(payload.pos) instanceof NativeTurntableEntity owner) {
                owner.readAngle(payload.key, payload.sequence, payload.angle);
            }
        }));
    }
}
