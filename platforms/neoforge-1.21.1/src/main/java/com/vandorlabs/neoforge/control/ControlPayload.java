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
public record ControlPayload(int menuId,BlockPos pos,String channels,int height,int tilt,int direction,int low,int high,int controlType,int size) implements CustomPacketPayload {
    public ControlPayload(int menuId,BlockPos pos,String channels,int height,int tilt,int direction){this(menuId,pos,channels,height,tilt,direction,5,15,0,0);}
    public static final Type<ControlPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:mounted_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ControlPayload> CODEC=new StreamCodec<>(){
        public ControlPayload decode(RegistryFriendlyByteBuf b){return new ControlPayload(b.readVarInt(),b.readBlockPos(),b.readUtf(ChannelList.MAX_TEXT_LENGTH),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte());}
        public void encode(RegistryFriendlyByteBuf b,ControlPayload p){b.writeVarInt(p.menuId);b.writeBlockPos(p.pos);b.writeUtf(p.channels,ChannelList.MAX_TEXT_LENGTH);b.writeByte(p.height);b.writeByte(p.tilt);b.writeByte(p.direction);b.writeByte(p.low);b.writeByte(p.high);b.writeByte(p.controlType);b.writeByte(p.size);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,p);}));}
    public static boolean apply(ServerPlayer player,ControlPayload p){if(!(player.containerMenu instanceof ControlMenu menu)||menu.containerId!=p.menuId||!menu.pos.equals(p.pos)||!menu.stillValid(player)||!ControlMount.valid(p.height,p.tilt,p.direction)||!com.vandorlabs.redstone.RedstoneScreenPolicy.validLimits(p.low,p.high)||!com.vandorlabs.persistence.ControlSettings.validType(p.controlType)||!com.vandorlabs.persistence.ControlSettings.validSize(p.size))return false;ChannelList channels=ChannelList.parse(p.channels);return channels!=null&&menu.tile.configureOptions(channels,p.height,p.tilt,p.direction,p.low,p.high,p.controlType,p.size);}
}
