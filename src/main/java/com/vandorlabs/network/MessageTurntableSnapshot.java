package com.vandorlabs.network;

import com.vandorlabs.turntable.EntityTurntable;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.Arrays;

/** One requested bounded slice at a time; no unsolicited multi-megabyte spawn packets. */
public final class MessageTurntableSnapshot implements IMessage {
    public int entity,total,offset;public byte[] data=new byte[0];
    public MessageTurntableSnapshot(){}
    public static MessageTurntableSnapshot request(int id,int offset){MessageTurntableSnapshot result=new MessageTurntableSnapshot();result.entity=id;result.offset=offset;return result;}
    @Override public void fromBytes(ByteBuf b){entity=b.readInt();total=b.readInt();offset=b.readInt();int size=b.readInt();if(size<0||size>60000||size!=b.readableBytes())throw new IllegalArgumentException("Invalid snapshot slice");data=new byte[size];b.readBytes(data);}
    @Override public void toBytes(ByteBuf b){b.writeInt(entity);b.writeInt(total);b.writeInt(offset);b.writeInt(data.length);b.writeBytes(data);}
    public static final class Server implements IMessageHandler<MessageTurntableSnapshot,IMessage>{
        public IMessage onMessage(MessageTurntableSnapshot packet,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{
            if(packet.total!=0||packet.data.length!=0||packet.offset<0)return;net.minecraft.entity.Entity raw=player.world.getEntityByID(packet.entity);if(!(raw instanceof EntityTurntable)||raw.getDistanceSq(player)>128*128)return;
            EntityTurntable entity=(EntityTurntable)raw;if(!entity.allowSnapshotRequest(player))return;
            try{byte[] bytes=entity.wire();if(bytes==null||packet.offset>=bytes.length)return;MessageTurntableSnapshot response=request(packet.entity,packet.offset);response.total=bytes.length;response.data=Arrays.copyOfRange(bytes,packet.offset,Math.min(bytes.length,packet.offset+60000));PacketHandler.INSTANCE.sendTo(response,player);}catch(java.io.IOException failure){com.vandorlabs.VandorLabs.logger.warn("Cannot synchronize captured turntable",failure);}
        });return null;}
    }
    public static final class Client implements IMessageHandler<MessageTurntableSnapshot,IMessage>{public IMessage onMessage(MessageTurntableSnapshot packet,MessageContext context){com.vandorlabs.VandorLabs.proxy.turntablePacket(packet);return null;}}
}
