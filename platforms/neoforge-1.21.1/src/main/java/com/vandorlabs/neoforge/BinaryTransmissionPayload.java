package com.vandorlabs.neoforge;

import com.vandorlabs.redstone.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Immediate inline transmission settings, separate from uncommitted mount controls. */
public record BinaryTransmissionPayload(BlockPos pos,int windowId,ChannelList channels,int range,boolean local) implements CustomPacketPayload {
    public static final Type<BinaryTransmissionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","binary_transmission"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BinaryTransmissionPayload> CODEC=new StreamCodec<>(){
        public BinaryTransmissionPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()<16)throw new IllegalArgumentException("Truncated transmission settings");var pos=b.readBlockPos();int window=b.readInt(),range=b.readUnsignedShort(),local=b.readUnsignedByte(),count=b.readUnsignedByte();if(local>1||!TransmissionSettings.validRange(range)||count>64||b.readableBytes()!=count*4)throw new IllegalArgumentException("Invalid transmission settings");int[] values=new int[count];for(int i=0;i<count;i++)values[i]=b.readInt();return new BinaryTransmissionPayload(pos,window,ChannelList.of(values),range,local==1);}
        public void encode(RegistryFriendlyByteBuf b,BinaryTransmissionPayload p){b.writeBlockPos(p.pos);b.writeInt(p.windowId);b.writeShort(p.range);b.writeBoolean(p.local);b.writeByte(p.channels.size());for(int i=0;i<p.channels.size();i++)b.writeInt(p.channels.get(i));}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    static boolean apply(ServerPlayer player,BinaryTransmissionPayload p){if(p.pos==null||!TransmissionSettings.validConfiguration(p.channels,p.range))return false;var tile=BinaryControlMenu.editing(player,p.pos,p.windowId);return tile!=null&&tile.configureTransmission(p.channels,p.range,p.local);}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p);}));}
}
