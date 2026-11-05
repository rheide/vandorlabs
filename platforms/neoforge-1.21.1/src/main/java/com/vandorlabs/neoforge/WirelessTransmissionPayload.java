package com.vandorlabs.neoforge;

import com.vandorlabs.redstone.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Same bounded inline frame, with a distinct endpoint packet identity and exact endpoint-menu authorization. */
public record WirelessTransmissionPayload(BlockPos pos,int windowId,ChannelList channels,int range,boolean local) implements CustomPacketPayload {
    public static final Type<WirelessTransmissionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","wireless_transmission"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WirelessTransmissionPayload> CODEC=BinaryTransmissionPayload.CODEC.map(p->new WirelessTransmissionPayload(p.pos(),p.windowId(),p.channels(),p.range(),p.local()),p->new BinaryTransmissionPayload(p.pos(),p.windowId(),p.channels(),p.range(),p.local()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    static boolean apply(ServerPlayer player,WirelessTransmissionPayload p){if(p.pos==null||!TransmissionSettings.validConfiguration(p.channels,p.range))return false;var tile=WirelessMenu.editing(player,p.pos,p.windowId);return tile!=null&&tile.configureTransmission(p.channels,p.range,p.local);}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p);}));}
}
