package com.vandorlabs.client;

import com.vandorlabs.network.*;
import com.vandorlabs.vehicle.VehicleNetwork;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import java.util.concurrent.atomic.AtomicLongArray;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Fixture-only outbound FML payload measurements; never reads or changes game state on Netty threads. */
final class CouplerWireProbe {
    private static final String[] PHASES={"motion","gui-open","gui-edit","channel-input","manual-input"};
    private static final String[] TYPES={"coupler-config","vehicle-tile","vehicle-snapshot","gear-motion","vehicle-state","other"};
    private static final AtomicLongArray counts=new AtomicLongArray(60),bytes=new AtomicLongArray(60);
    private static volatile int phase;private static Channel client,server;private static int vehicleId,couplerId,gearId;
    private static int discriminator(IMessage message){FMLProxyPacket packet=(FMLProxyPacket)PacketHandler.INSTANCE.getPacketFrom(message);ByteBuf payload=packet.payload();try{return payload.getUnsignedByte(payload.readerIndex());}finally{payload.release();}}
    static void install(Minecraft mc)throws Exception{
        if(System.getProperty("vandorlabs.reprolab")==null)throw new IllegalStateException("Repro-only wire probe");vehicleId=discriminator(new VehicleNetwork.Packet(VehicleNetwork.TILE));couplerId=discriminator(new com.vandorlabs.coupler.MessageCoupler(-1,"",true,true));gearId=discriminator(new MessageGearMotion());
        client=mc.getConnection().getNetworkManager().channel();server=mc.getIntegratedServer().callFromMainThread(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).connection.netManager.channel()).get();attach(client,0);attach(server,1);
    }
    private static void attach(Channel channel,int direction)throws Exception{channel.eventLoop().submit(()->channel.pipeline().addLast("vandor_coupler_wire",new ChannelOutboundHandlerAdapter(){@Override public void write(ChannelHandlerContext context,Object message,ChannelPromise promise)throws Exception{ByteBuf payload=null;String name=null;if(message instanceof FMLProxyPacket){payload=((FMLProxyPacket)message).payload();name=((FMLProxyPacket)message).channel();}else if(message instanceof net.minecraft.network.play.server.SPacketCustomPayload){payload=((net.minecraft.network.play.server.SPacketCustomPayload)message).getBufferData();name=((net.minecraft.network.play.server.SPacketCustomPayload)message).getChannelName();}else if(message instanceof net.minecraft.network.play.client.CPacketCustomPayload){payload=((net.minecraft.network.play.client.CPacketCustomPayload)message).getBufferData();name=((net.minecraft.network.play.client.CPacketCustomPayload)message).getChannelName();}if(payload!=null&&"vandorlabs".equals(name)&&payload.isReadable()){int at=payload.readerIndex(),id=payload.getUnsignedByte(at),type=5;if(id==couplerId)type=0;else if(id==gearId)type=3;else if(id==vehicleId&&payload.readableBytes()>1){int action=payload.getUnsignedByte(at+1);type=action==VehicleNetwork.TILE?1:action==VehicleNetwork.SNAPSHOT?2:action==VehicleNetwork.STATE?4:5;}int index=(phase*2+direction)*6+type;counts.incrementAndGet(index);bytes.addAndGet(index,payload.readableBytes());}context.write(message,promise);}})).sync();}
    private static void drain(Channel channel)throws Exception{if(channel!=null&&!channel.eventLoop().inEventLoop())channel.eventLoop().submit(()->{}).sync();}
    static void phase(int next)throws Exception{drain(client);drain(server);phase=next;}
    static void dump()throws Exception{drain(client);drain(server);for(int p=0;p<PHASES.length;p++)for(int direction=0;direction<2;direction++)for(int type=0;type<TYPES.length;type++){int index=(p*2+direction)*6+type;long count=counts.get(index);if(count!=0)System.out.println("[vandorlabs][reprolab] coupler-wire phase="+PHASES[p]+" direction="+(direction==0?"C2S":"S2C")+" type="+TYPES[type]+" messages="+count+" fmlPayloadBytes="+bytes.get(index));}}
}
