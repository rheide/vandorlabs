package com.vandorlabs.neoforge.control;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.ControlMount;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public record ControlPayload(int menuId,BlockPos pos,String channels,int height,int tilt,int direction) implements CustomPacketPayload {
    public static final Type<ControlPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:mounted_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ControlPayload> CODEC=new StreamCodec<>(){
        public ControlPayload decode(RegistryFriendlyByteBuf b){return new ControlPayload(b.readVarInt(),b.readBlockPos(),b.readUtf(ChannelList.MAX_TEXT_LENGTH),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte());}
        public void encode(RegistryFriendlyByteBuf b,ControlPayload p){b.writeVarInt(p.menuId);b.writeBlockPos(p.pos);b.writeUtf(p.channels,ChannelList.MAX_TEXT_LENGTH);b.writeByte(p.height);b.writeByte(p.tilt);b.writeByte(p.direction);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,p);}));}
    public static boolean apply(ServerPlayer player,ControlPayload p){if(!(player.containerMenu instanceof ControlMenu menu)||menu.containerId!=p.menuId||!menu.pos.equals(p.pos)||!menu.stillValid(player)||!ControlMount.valid(p.height,p.tilt,p.direction))return false;ChannelList channels=ChannelList.parse(p.channels);return channels!=null&&menu.tile.configure(channels,p.height,p.tilt,p.direction);}
}
