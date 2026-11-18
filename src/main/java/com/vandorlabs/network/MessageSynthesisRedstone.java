package com.vandorlabs.network;

import com.vandorlabs.container.ContainerSynthesisTower;
import com.vandorlabs.container.ContainerChipAppliance;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Bounded receiving-channel bank and activation; processing state remains server-owned. */
public final class MessageSynthesisRedstone implements IMessage {
    private int window,mode;
    private BlockPos pos;
    private ChannelList channels;
    public MessageSynthesisRedstone(){}
    public MessageSynthesisRedstone(int window,BlockPos pos,int mode,ChannelList channels){this.window=window;this.pos=pos;this.mode=mode;this.channels=channels;}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeLong(pos.toLong());b.writeByte(mode);b.writeByte(channels.size());for(int channel:channels.toArray())b.writeInt(channel);}
    @Override public void fromBytes(ByteBuf b){
        if(b.readableBytes()<14)throw new IllegalArgumentException("Truncated Synthesis Redstone settings");
        window=b.readInt();pos=BlockPos.fromLong(b.readLong());mode=b.readUnsignedByte();int count=b.readUnsignedByte();
        if(mode>2||count>ChannelList.MAX_CHANNELS||b.readableBytes()!=count*4)throw new IllegalArgumentException("Malformed Synthesis Redstone settings");
        int[] values=new int[count];for(int i=0;i<count;i++)values[i]=b.readInt();channels=ChannelList.of(values);
    }
    public static boolean apply(EntityPlayerMP player,MessageSynthesisRedstone message){
        if(!player.getServerWorld().isCallingFromMinecraftThread()||message.pos==null||message.channels==null||message.mode<0||message.mode>2)return false;
        if(player.openContainer instanceof ContainerChipAppliance){
            ContainerChipAppliance container=(ContainerChipAppliance)player.openContainer;
            if(container.windowId!=message.window||!container.tile.getPos().equals(message.pos)||!container.canInteractWith(player)
                    ||!ConfigurationAccess.canEditDialog(player,player.world,message.pos))return false;
            container.tile.configureRedstone(message.mode,message.channels);return true;
        }
        if(!(player.openContainer instanceof ContainerSynthesisTower))return false;
        ContainerSynthesisTower container=(ContainerSynthesisTower)player.openContainer;
        if(container.windowId!=message.window||!container.tile.getPos().equals(message.pos)||!container.canInteractWith(player))return false;
        container.tile.configureSynthesisRedstone(message.mode,message.channels);return true;
    }
    public static final class Handler implements IMessageHandler<MessageSynthesisRedstone,IMessage>{
        @Override public IMessage onMessage(MessageSynthesisRedstone message,MessageContext context){EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->apply(player,message));return null;}
    }
}
