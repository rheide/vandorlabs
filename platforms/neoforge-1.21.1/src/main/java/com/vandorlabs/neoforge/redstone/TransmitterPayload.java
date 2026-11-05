package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded channel draft bound to the current exact handheld editing session. */
public record TransmitterPayload(int window,String channels) implements CustomPacketPayload {
    public static final Type<TransmitterPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","transmitter_channels"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TransmitterPayload> CODEC=new StreamCodec<>(){
        public TransmitterPayload decode(RegistryFriendlyByteBuf buffer){int window=buffer.readVarInt();String text=buffer.readUtf(ChannelList.MAX_TEXT_LENGTH);if(buffer.isReadable()||ChannelList.parse(text)==null)throw new IllegalArgumentException("Malformed transmitter channels");return new TransmitterPayload(window,text);}
        public void encode(RegistryFriendlyByteBuf buffer,TransmitterPayload packet){buffer.writeVarInt(packet.window);buffer.writeUtf(packet.channels,ChannelList.MAX_TEXT_LENGTH);}
    };
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,TransmitterPayload::handle);}
    public static boolean apply(ServerPlayer player,TransmitterPayload packet){ChannelList channels=ChannelList.parse(packet.channels);return player.getServer().isSameThread()&&channels!=null&&player.containerMenu.containerId==packet.window&&player.containerMenu instanceof TransmitterMenu menu&&menu.configure(player,channels);}
    private static void handle(TransmitterPayload packet,IPayloadContext context){context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,packet);});}
}
