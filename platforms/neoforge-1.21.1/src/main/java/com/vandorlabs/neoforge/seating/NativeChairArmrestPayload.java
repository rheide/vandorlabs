package com.vandorlabs.neoforge.seating;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record NativeChairArmrestPayload(BlockPos pos, int window, int index, int min, int max, ChannelList channels)
    implements CustomPacketPayload {
    public NativeChairArmrestPayload {
        if (pos == null || channels == null || (index != NativeChairArmrest.LEFT && index != NativeChairArmrest.RIGHT)
            || min < 0 || max > 15 || max - min < 2) throw new IllegalArgumentException("Invalid armrest settings");
    }

    private static final Type<NativeChairArmrestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("vandorlabs", "chair_armrest_settings"));
    private static final StreamCodec<RegistryFriendlyByteBuf, NativeChairArmrestPayload> CODEC = new StreamCodec<>() {
        @Override public NativeChairArmrestPayload decode(RegistryFriendlyByteBuf buffer) {
            var pos = buffer.readBlockPos();
            int window = buffer.readInt(), index = buffer.readUnsignedByte(), min = buffer.readUnsignedByte();
            int max = buffer.readUnsignedByte(), count = buffer.readUnsignedByte();
            if (count > ChannelList.MAX_CHANNELS || buffer.readableBytes() != count * 4)
                throw new IllegalArgumentException("Invalid armrest channel count");
            int[] channels = new int[count];
            for (int i = 0; i < count; i++) channels[i] = buffer.readInt();
            return new NativeChairArmrestPayload(pos, window, index, min, max, ChannelList.of(channels));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, NativeChairArmrestPayload message) {
            buffer.writeBlockPos(message.pos);
            buffer.writeInt(message.window);
            buffer.writeByte(message.index);
            buffer.writeByte(message.min);
            buffer.writeByte(message.max);
            var channels = message.channels.toArray();
            buffer.writeByte(channels.length);
            for (int channel : channels) buffer.writeInt(channel);
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static boolean apply(ServerPlayer actor, NativeChairArmrestPayload message) {
        var menu = NativeSeatMenu.editing(actor, message.pos, message.window);
        if (menu == null || menu.tile.kind() != NativeSeatKind.ADVANCED) return false;
        var arm = menu.tile.armrest(message.index);
        return arm != null && arm.configure(message.channels, message.min, message.max);
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (message, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer actor) apply(actor, message);
        }));
    }
}
