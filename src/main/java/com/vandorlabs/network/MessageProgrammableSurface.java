package com.vandorlabs.network;

import com.vandorlabs.container.ContainerSurface;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.nio.charset.StandardCharsets;

/** Fixed scalar settings and bounded names; world access occurs only on the server thread. */
public final class MessageProgrammableSurface implements IMessage {
    private int window,layout;private String on,off;
    public MessageProgrammableSurface(){}
    public MessageProgrammableSurface(int window,String on,String off,int layout){this.window=window;this.on=on;this.off=off;this.layout=layout;}
    private static String read(ByteBuf input){int length=input.readUnsignedShort();if(length>256||input.readableBytes()<length)throw new IllegalArgumentException("Surface texture name too long");byte[] bytes=new byte[length];input.readBytes(bytes);return new String(bytes,StandardCharsets.UTF_8);}
    private static void write(ByteBuf output,String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>256)throw new IllegalArgumentException("Surface texture name too long");output.writeShort(bytes.length);output.writeBytes(bytes);}
    @Override public void fromBytes(ByteBuf input){window=input.readInt();layout=input.readUnsignedByte();if(layout>2)throw new IllegalArgumentException("Invalid surface layout");on=read(input);off=read(input);}
    @Override public void toBytes(ByteBuf output){output.writeInt(window);output.writeByte(layout);write(output,on);write(output,off);}
    public static final class Handler implements IMessageHandler<MessageProgrammableSurface,IMessage>{
        public IMessage onMessage(MessageProgrammableSurface message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP actor=context.getServerHandler().player;actor.getServerWorld().addScheduledTask(()->{
            if(actor.openContainer.windowId!=message.window||!(actor.openContainer instanceof ContainerSurface))return;ContainerSurface container=(ContainerSurface)actor.openContainer;if(!container.canInteractWith(actor))return;
            try{container.tile.configure(message.on,message.off,message.layout,actor);}catch(IllegalArgumentException denied){actor.sendStatusMessage(new net.minecraft.util.text.TextComponentString(denied.getMessage()),true);}
        });return null;}
    }
}
