package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.network.PacketHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

/** Bounded actual outbound Source/STATE observation. Netty reads detached packet bytes only. */
final class VehicleScopedSplitWireChecks {
    private static final List<Seen> PACKETS=new ArrayList<>();private static Channel channel;private static int discriminator;private static volatile String failure;
    private static final class Seen{final int type;final long tick;final UUID owner,scope;Seen(VehicleNetwork.Packet p){type=p.type;tick=p.serverTick;owner=p.token;scope=p.associationGeneration;}}
    static void install(Minecraft mc)throws Exception{
        FMLProxyPacket sample=(FMLProxyPacket)PacketHandler.INSTANCE.getPacketFrom(new VehicleNetwork.Packet(VehicleNetwork.STATE));try{discriminator=sample.payload().getUnsignedByte(sample.payload().readerIndex());}finally{sample.payload().release();}
        channel=mc.getIntegratedServer().callFromMainThread(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).connection.netManager.channel()).get();
        channel.eventLoop().submit(()->channel.pipeline().addLast("vandor_scoped_split_wire",new ChannelOutboundHandlerAdapter(){@Override public void write(ChannelHandlerContext context,Object message,ChannelPromise promise)throws Exception{
            ByteBuf raw=null;String name=null;if(message instanceof FMLProxyPacket){raw=((FMLProxyPacket)message).payload();name=((FMLProxyPacket)message).channel();}else if(message instanceof net.minecraft.network.play.server.SPacketCustomPayload){raw=((net.minecraft.network.play.server.SPacketCustomPayload)message).getBufferData();name=((net.minecraft.network.play.server.SPacketCustomPayload)message).getChannelName();}
            if(raw!=null&&"vandorlabs".equals(name)&&raw.readableBytes()>1&&raw.getUnsignedByte(raw.readerIndex())==discriminator){int type=raw.getUnsignedByte(raw.readerIndex()+1);if(type==VehicleNetwork.SNAPSHOT||type==VehicleNetwork.STATE){ByteBuf copy=raw.duplicate();copy.skipBytes(1);VehicleNetwork.Packet packet=new VehicleNetwork.Packet();packet.fromBytes(copy);synchronized(PACKETS){if(!packet.valid)failure="actual outbound Source/STATE failed roundtrip";else if(PACKETS.size()>=8192)failure="outbound probe bound";else PACKETS.add(new Seen(packet));}}}context.write(message,promise);
        }})).sync();
    }
    static void verify()throws Exception{
        channel.eventLoop().submit(()->{}).sync();if(failure!=null)throw new IllegalStateException(failure);int snapshots=0,states=0;UUID first=VehicleScopedSplitRecoveryChecks.firstScope(),second=VehicleScopedSplitRecoveryChecks.secondScope();
        synchronized(PACKETS){for(Seen p:PACKETS){if(Objects.equals(p.scope,first)||Objects.equals(p.scope,second))throw new IllegalStateException("Staged split generation leaked before second-output rollback: type="+p.type+" wireTick="+p.tick+" owner="+p.owner+" generation="+p.scope+" journalTick="+VehicleScopedSplitRecoveryChecks.journalTick()+" rollbackTick="+VehicleScopedSplitRecoveryChecks.rollbackTick());if(p.type==VehicleNetwork.SNAPSHOT)snapshots++;else states++;}}
        if(states==0||snapshots==0)throw new IllegalStateException("Probe must observe real recovery Source and STATE, Source="+snapshots+" STATE="+states);channel.eventLoop().submit(()->channel.pipeline().remove("vandor_scoped_split_wire")).sync();System.out.println("[vandorlabs][reprolab] scoped-split-wire PASS SourceFragments="+snapshots+" STATE="+states+" noStagedGeneration/sameTickNotUUIDOrder");
    }
    private VehicleScopedSplitWireChecks(){}
}
