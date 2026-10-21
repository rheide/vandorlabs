package com.vandorlabs.network;

import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageTokenSlot implements IMessage {
    private int window,mode,mask;private boolean any;private String channels;
    public MessageTokenSlot(){}
    public MessageTokenSlot(int window,int mode,boolean any,int mask,String channels){this.window=window;this.mode=mode;this.any=any;this.mask=mask;this.channels=channels;}
    @Override public void fromBytes(ByteBuf b){window=b.readInt();mode=b.readUnsignedByte();any=b.readBoolean();mask=b.readUnsignedByte();channels=ByteBufUtils.readUTF8String(b);}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeByte(mode);b.writeBoolean(any);b.writeByte(mask);ByteBufUtils.writeUTF8String(b,channels);}
    public static final class Handler implements IMessageHandler<MessageTokenSlot,IMessage>{
        public IMessage onMessage(MessageTokenSlot message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{
            if(player.openContainer.windowId!=message.window||!(player.openContainer instanceof ContainerTokenSlot)||message.mode>2||message.mask>127||message.channels.length()>256)return;
            ContainerTokenSlot container=(ContainerTokenSlot)player.openContainer;ChannelList channels=ChannelList.parse(message.channels);if(!container.canInteractWith(player)||channels==null)return;
            container.tile.configure(message.any,message.mask,message.mode);container.tile.setRedstoneChannels(channels);
        });return null;}
    }
}
