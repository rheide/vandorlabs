package com.vandorlabs.neoforge.trigger;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.redstone.ChannelList;
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
public record TriggerPayload(int menuId,BlockPos pos,int off,int on,int low,int medium,boolean bands,int exact,String channels) implements CustomPacketPayload {
    public static final Type<TriggerPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:trigger_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TriggerPayload> CODEC=new StreamCodec<>(){
        public TriggerPayload decode(RegistryFriendlyByteBuf b){return new TriggerPayload(b.readVarInt(),b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readByte(),b.readUtf(ChannelList.MAX_TEXT_LENGTH));}
        public void encode(RegistryFriendlyByteBuf b,TriggerPayload p){b.writeVarInt(p.menuId);b.writeBlockPos(p.pos);b.writeVarInt(p.off);b.writeVarInt(p.on);b.writeVarInt(p.low);b.writeVarInt(p.medium);b.writeBoolean(p.bands);b.writeByte(p.exact);b.writeUtf(p.channels,ChannelList.MAX_TEXT_LENGTH);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,p);}));}
    public static boolean apply(ServerPlayer player,TriggerPayload p){if(!(player.containerMenu instanceof TriggerMenu menu)||menu.containerId!=p.menuId||!menu.pos.equals(p.pos)||!menu.stillValid(player)||p.exact< -1||p.exact>15||!ModernFinishCatalog.validChoice(p.off)||!ModernFinishCatalog.validChoice(p.on)||!ModernFinishCatalog.validChoice(p.low)||!ModernFinishCatalog.validChoice(p.medium))return false;ChannelList channels=ChannelList.parse(p.channels);return channels!=null&&menu.tile.configure(p.off,p.on,p.low,p.medium,p.bands,p.exact,channels);}
}
