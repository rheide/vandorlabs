package com.vandorlabs.plate;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed settings plus a bounded channel list, applied only through the authorized live dialog. */
public final class PlateMessage implements IMessage {
    private int window,texture,mode,sensitivity;private String channels="";private boolean valid;
    public PlateMessage(){}
    public PlateMessage(int window,int texture,int mode,String channels){this(window,texture,mode,0,channels);}
    public PlateMessage(int window,int texture,int mode,int sensitivity,String channels){this.sensitivity=sensitivity;this.window=window;this.texture=texture;this.mode=mode;this.channels=channels;valid=true;}
    @Override public void fromBytes(ByteBuf b){valid=false;try{window=b.readInt();texture=b.readInt();mode=b.readUnsignedByte();sensitivity=b.readUnsignedByte();int length=b.readUnsignedShort();if(length>256||length!=b.readableBytes())return;channels=b.readCharSequence(length,StandardCharsets.US_ASCII).toString();valid=true;}catch(RuntimeException malformed){valid=false;}}
    @Override public void toBytes(ByteBuf b){byte[] text=channels.getBytes(StandardCharsets.US_ASCII);if(text.length>256)throw new IllegalArgumentException("Channel list too long");b.writeInt(window).writeInt(texture).writeByte(mode).writeByte(sensitivity).writeShort(text.length).writeBytes(text);}
    public static final class Handler implements IMessageHandler<PlateMessage,IMessage>{
        @Override public IMessage onMessage(PlateMessage message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{
            if(!message.valid||!(player.openContainer instanceof PlateContainer)||player.openContainer.windowId!=message.window)return;
            PlateContainer container=(PlateContainer)player.openContainer;com.vandorlabs.redstone.ChannelList list=com.vandorlabs.redstone.ChannelList.parse(message.channels);
            if(!container.canInteractWith(player)||list==null||message.mode>2||message.sensitivity>2||!com.vandorlabs.tiles.ScreenHousingTextures.validChoice(message.texture))return;container.tile.configure(message.texture,message.mode,message.sensitivity,list);
        });return null;}
    }
}
