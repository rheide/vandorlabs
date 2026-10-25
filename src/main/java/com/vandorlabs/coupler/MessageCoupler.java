package com.vandorlabs.coupler;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Bounded channel configuration, applied to the authorized live container on the server. */
public final class MessageCoupler implements IMessage {
    private int window;private String channels;private boolean inverted;
    public MessageCoupler(){}
    public MessageCoupler(int window,String channels,boolean inverted){this.window=window;this.channels=channels;this.inverted=inverted;}
    @Override public void fromBytes(ByteBuf b){window=b.readInt();int length=b.readUnsignedShort();if(length>256||b.readableBytes()!=length+1)throw new IllegalArgumentException("Invalid coupler configuration length");channels=b.readCharSequence(length,StandardCharsets.US_ASCII).toString();inverted=b.readBoolean();}
    @Override public void toBytes(ByteBuf b){byte[] bytes=channels.getBytes(StandardCharsets.US_ASCII);if(bytes.length>256)throw new IllegalArgumentException("Channel list too long");b.writeInt(window);b.writeShort(bytes.length);b.writeBytes(bytes);b.writeBoolean(inverted);}
    public static final class Handler implements IMessageHandler<MessageCoupler,IMessage>{
        @Override public IMessage onMessage(MessageCoupler message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP actor=context.getServerHandler().player;actor.getServerWorld().addScheduledTask(()->{
            if(!(actor.openContainer instanceof ContainerCoupler)||actor.openContainer.windowId!=message.window)return;
            ContainerCoupler container=(ContainerCoupler)actor.openContainer;
            com.vandorlabs.redstone.ChannelList list=com.vandorlabs.redstone.ChannelList.parse(message.channels);
            if(list!=null&&container.canInteractWith(actor))container.tile.configure(list,message.inverted);
        });return null;}
    }
}
