package com.vandorlabs.neoforge.vehicle;

import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Fixed-size seed terrain projection sent only to the mounted pilot. */
@EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD)
public record NativeCruiseTerrain(int vehicle, int x, int z, int step, int[] pixels) implements CustomPacketPayload {
    public static final int SIZE = 32;
    public static Consumer<NativeCruiseTerrain> receiver = unused -> {};
    public static final Type<NativeCruiseTerrain> TYPE = new Type<>(ResourceLocation.parse("vandorlabs:cruise_terrain"));
    private static final StreamCodec<RegistryFriendlyByteBuf, NativeCruiseTerrain> CODEC = new StreamCodec<>() {
        @Override public NativeCruiseTerrain decode(RegistryFriendlyByteBuf buffer) {
            if (buffer.readableBytes() != 16 + 4 * SIZE * SIZE) throw new IllegalArgumentException("Invalid cruise terrain length");
            int vehicle = buffer.readInt(), x = buffer.readInt(), z = buffer.readInt(), step = buffer.readInt();
            int[] pixels = new int[SIZE * SIZE];
            for (int index = 0; index < pixels.length; index++) pixels[index] = buffer.readInt();
            return new NativeCruiseTerrain(vehicle, x, z, step, pixels);
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, NativeCruiseTerrain terrain) {
            buffer.writeInt(terrain.vehicle).writeInt(terrain.x).writeInt(terrain.z).writeInt(terrain.step);
            for (int color : terrain.pixels) buffer.writeInt(color);
        }
    };

    public NativeCruiseTerrain {
        if (vehicle < 0 || Math.abs((long)x) > 31000000 || Math.abs((long)z) > 31000000
                || step < 16 || step > 1024 || Integer.bitCount(step) != 1 || pixels.length != SIZE * SIZE) {
            throw new IllegalArgumentException("Invalid cruise terrain projection");
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (terrain, context) -> context.enqueueWork(() -> receiver.accept(terrain)));
    }
}
