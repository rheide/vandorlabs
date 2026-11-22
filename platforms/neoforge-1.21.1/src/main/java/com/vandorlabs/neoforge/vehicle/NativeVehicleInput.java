package com.vandorlabs.neoforge.vehicle;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A short held-key sample. Position, attitude, permission and movement belong to the server. */
@EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD)
public record NativeVehicleInput(int entity, int keys) implements CustomPacketPayload {
    public static final int FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8, BRAKE = 16, USE = 32;
    public static final Type<NativeVehicleInput> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs", "vehicle_input"));
    private static final StreamCodec<RegistryFriendlyByteBuf, NativeVehicleInput> CODEC = new StreamCodec<>() {
        @Override public NativeVehicleInput decode(RegistryFriendlyByteBuf buffer) {
            if (buffer.readableBytes() != 5) throw new IllegalArgumentException("Invalid vehicle input packet length");
            return new NativeVehicleInput(buffer.readInt(), buffer.readUnsignedByte());
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, NativeVehicleInput input) {
            buffer.writeInt(input.entity);
            buffer.writeByte(input.keys);
        }
    };

    public NativeVehicleInput {
        if (entity < 0 || keys < 0 || keys > USE) throw new IllegalArgumentException("Invalid vehicle input");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (input, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer pilot
                    && pilot.getVehicle() instanceof NativeGroundVehicle craft && craft.getId() == input.entity) {
                craft.input(pilot, input.keys);
            }
        }));
    }
}
