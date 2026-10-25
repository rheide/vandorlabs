package com.vandorlabs.copier;

import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed-size settings plus a bounded textual list; no tile data is accepted from clients. */
public final class CopyMessage implements IMessage {
    private int window,operation,height,increment,mode;private ChannelList channels;
    public CopyMessage(){}
    public CopyMessage(int window,int operation,int height,int increment,int mode,ChannelList channels){this.window=window;this.operation=operation;this.height=height;this.increment=increment;this.mode=mode;this.channels=channels;}
    @Override public void toBytes(ByteBuf out){byte[] text=channels.toString().getBytes(StandardCharsets.UTF_8);out.writeInt(window);out.writeByte(operation);out.writeByte(height);out.writeInt(increment);out.writeByte(mode);out.writeShort(text.length);out.writeBytes(text);}
    @Override public void fromBytes(ByteBuf in){if(in.readableBytes()<13)throw new IllegalArgumentException("Truncated copy settings");window=in.readInt();operation=in.readUnsignedByte();height=in.readUnsignedByte();increment=in.readInt();mode=in.readUnsignedByte();int length=in.readUnsignedShort();if(operation>2||height<1||height>16||increment < -65534||increment > 65534||mode>2||length>ChannelList.MAX_TEXT_LENGTH||length!=in.readableBytes())throw new IllegalArgumentException("Invalid or oversized copy settings");byte[] text=new byte[length];in.readBytes(text);channels=ChannelList.parse(new String(text,StandardCharsets.UTF_8));if(channels==null)throw new IllegalArgumentException("Invalid copy channel list");}
    public static final class Handler implements IMessageHandler<CopyMessage,IMessage>{public IMessage onMessage(CopyMessage message,MessageContext context){EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{if(player.openContainer.windowId!=message.window||!(player.openContainer instanceof CopyControllerContainer))return;CopyControllerContainer container=(CopyControllerContainer)player.openContainer;try{container.update(player,message.operation,message.height,message.increment,message.mode,message.channels);}catch(Exception failure){container.tile.failure(failure.getMessage());}});return null;}}
}
