package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.ControlMountSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Atomic General-page limits, type and mount; existing binary/power payloads remain unchanged. */
public record SignalControlPayload(BlockPos pos,int windowId,ControlMountSettings mount,int low,int high,int controlType) implements CustomPacketPayload {
    public static final Type<SignalControlPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","signal_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SignalControlPayload> CODEC=new StreamCodec<>(){
        public SignalControlPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=18)throw new IllegalArgumentException("Invalid signal control payload length");var pos=b.readBlockPos();int window=b.readInt(),height=b.readUnsignedByte(),tilt=b.readUnsignedByte(),direction=b.readUnsignedByte(),low=b.readUnsignedByte(),high=b.readUnsignedByte(),type=b.readByte();if(!NativeSignalControlBlockEntity.validWireLimits(low,high)||type< -1||type>2)throw new IllegalArgumentException("Invalid signal control settings");return new SignalControlPayload(pos,window,ControlMountSettings.of(height,tilt,direction),low,high,type);}
        public void encode(RegistryFriendlyByteBuf b,SignalControlPayload p){b.writeBlockPos(p.pos);b.writeInt(p.windowId);b.writeByte(p.mount.height);b.writeByte(p.mount.tilt);b.writeByte(p.mount.direction);b.writeByte(p.low);b.writeByte(p.high);b.writeByte(p.controlType);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    static boolean apply(ServerPlayer player,SignalControlPayload p){if(p.pos==null||p.mount==null||!ControlMountSettings.valid(p.mount.height,p.mount.tilt,p.mount.direction)||!NativeSignalControlBlockEntity.validWireLimits(p.low,p.high)||p.controlType< -1||p.controlType>2)return false;var tile=BinaryControlMenu.editing(player,p.pos,p.windowId);return tile instanceof NativeSignalControlBlockEntity signal&&signal.configureSignal(p.mount,p.low,p.high,p.controlType);}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p);}));}
}
