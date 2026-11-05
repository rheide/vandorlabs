package com.vandorlabs.neoforge;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Immediate endpoint mode/levels/port and receiver selection, bounded before any channel allocation. */
public record WirelessPayload(BlockPos pos,int windowId,ChannelList receive,boolean analog,int on,int off,Direction port) implements CustomPacketPayload {
    public static final Type<WirelessPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","wireless_endpoint"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WirelessPayload> CODEC=new StreamCodec<>(){
        public WirelessPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()<17)throw new IllegalArgumentException("Truncated wireless endpoint settings");var pos=b.readBlockPos();int window=b.readInt(),analog=b.readUnsignedByte(),on=b.readUnsignedByte(),off=b.readUnsignedByte(),face=b.readUnsignedByte(),count=b.readUnsignedByte();if(analog>1||on>15||off>15||face>5||count>64||b.readableBytes()!=count*4)throw new IllegalArgumentException("Invalid wireless endpoint settings");int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=b.readInt();return new WirelessPayload(pos,window,ChannelList.of(channels),analog==1,on,off,Direction.from3DDataValue(face));}
        public void encode(RegistryFriendlyByteBuf b,WirelessPayload p){b.writeBlockPos(p.pos);b.writeInt(p.windowId);b.writeBoolean(p.analog);b.writeByte(p.on);b.writeByte(p.off);b.writeByte(p.port.get3DDataValue());b.writeByte(p.receive.size());for(int i=0;i<p.receive.size();i++)b.writeInt(p.receive.get(i));}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    static boolean apply(ServerPlayer player,WirelessPayload p){if(p.pos==null||p.receive==null||p.port==null||p.on<0||p.on>15||p.off<0||p.off>15)return false;var tile=WirelessMenu.editing(player,p.pos,p.windowId);return tile!=null&&tile.configure(p.receive,p.analog,p.on,p.off,p.port);}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p);}));}
}
