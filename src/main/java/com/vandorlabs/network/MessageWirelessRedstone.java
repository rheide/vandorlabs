package com.vandorlabs.network;

import com.vandorlabs.container.ContainerWirelessRedstone;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageWirelessRedstone implements IMessage {
    private int window,on,off,face;private boolean analog;private String channels;
    public MessageWirelessRedstone(){}
    public MessageWirelessRedstone(int window,String channels,boolean analog,int on,int off,int face){this.window=window;this.channels=channels;this.analog=analog;this.on=on;this.off=off;this.face=face;}
    public void fromBytes(ByteBuf b){window=b.readInt();channels=ByteBufUtils.readUTF8String(b);analog=b.readBoolean();on=b.readUnsignedByte();off=b.readUnsignedByte();face=b.readUnsignedByte();}
    public void toBytes(ByteBuf b){b.writeInt(window);ByteBufUtils.writeUTF8String(b,channels);b.writeBoolean(analog);b.writeByte(on);b.writeByte(off);b.writeByte(face);}
    public static final class Handler implements IMessageHandler<MessageWirelessRedstone,IMessage>{
        public IMessage onMessage(MessageWirelessRedstone message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{
            if(!(player.openContainer instanceof ContainerWirelessRedstone)||player.openContainer.windowId!=message.window||message.on>15||message.off>15||message.face>5||message.channels.length()>256)return;
            ContainerWirelessRedstone container=(ContainerWirelessRedstone)player.openContainer;ChannelList channels=ChannelList.parse(message.channels);if(!container.canInteractWith(player)||channels==null)return;
            container.tile.setRedstoneChannels(channels);container.tile.configure(message.analog,message.on,message.off);container.tile.rotatePort(net.minecraft.util.EnumFacing.getFront(message.face));
        });return null;}
    }
}
