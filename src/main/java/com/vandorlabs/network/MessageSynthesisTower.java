package com.vandorlabs.network;

import com.vandorlabs.container.ContainerSynthesisTower;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed-size design selection; no client-supplied inventory or processing values. */
public final class MessageSynthesisTower implements IMessage {
    private int window,design;
    private BlockPos pos;
    public MessageSynthesisTower(){}
    public MessageSynthesisTower(int window,BlockPos pos,int design){this.window=window;this.pos=pos;this.design=design;}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeLong(pos.toLong());b.writeByte(design);}
    @Override public void fromBytes(ByteBuf b){if(b.readableBytes()!=13)throw new IllegalArgumentException("Malformed Synthesis selection");window=b.readInt();pos=BlockPos.fromLong(b.readLong());design=b.readUnsignedByte();}
    public static boolean apply(EntityPlayerMP player,MessageSynthesisTower message){
        if(!player.getServerWorld().isCallingFromMinecraftThread()||message.pos==null||message.design<0||message.design>2||!(player.openContainer instanceof ContainerSynthesisTower))return false;
        ContainerSynthesisTower container=(ContainerSynthesisTower)player.openContainer;
        if(container.windowId!=message.window||!container.tile.getPos().equals(message.pos)||!container.canInteractWith(player))return false;
        container.tile.selectDesign(message.design);return true;
    }
    public static final class Handler implements IMessageHandler<MessageSynthesisTower,IMessage>{
        @Override public IMessage onMessage(MessageSynthesisTower message,MessageContext context){EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->apply(player,message));return null;}
    }
}
