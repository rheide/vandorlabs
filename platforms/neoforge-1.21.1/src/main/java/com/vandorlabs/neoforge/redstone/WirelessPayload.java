package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded native transport; permissions and exact menu identity are checked on the server thread. */
public record WirelessPayload(int window, String channels, boolean analog, int on, int off, int face) implements CustomPacketPayload {
    public static final Type<WirelessPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs", "wireless_redstone"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessPayload> CODEC = new StreamCodec<>() {
        @Override public WirelessPayload decode(RegistryFriendlyByteBuf buffer) {
            return new WirelessPayload(buffer.readInt(), buffer.readUtf(ChannelList.MAX_TEXT_LENGTH), buffer.readBoolean(), buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readUnsignedByte());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, WirelessPayload packet) {
            buffer.writeInt(packet.window); buffer.writeUtf(packet.channels, ChannelList.MAX_TEXT_LENGTH); buffer.writeBoolean(packet.analog);
            buffer.writeByte(packet.on); buffer.writeByte(packet.off); buffer.writeByte(packet.face);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) { event.registrar("1").playToServer(TYPE, CODEC, WirelessPayload::handle); }
    static boolean apply(ServerPlayer player, WirelessPayload packet) {
        if (!(player.containerMenu instanceof WirelessMenu menu) || menu.containerId != packet.window || !menu.stillValid(player)
                || packet.on < 0 || packet.on > 15 || packet.off < 0 || packet.off > 15
                || packet.face < 0 || packet.face > 5 || packet.channels.length() > ChannelList.MAX_TEXT_LENGTH) return false;
        ChannelList channels = ChannelList.parse(packet.channels);
        if (channels == null) return false;
        menu.tile.configure(channels, packet.analog, packet.on, packet.off, Direction.from3DDataValue(packet.face));
        return true;
    }
    private static void handle(WirelessPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) apply(player, packet);
        });
    }
}
