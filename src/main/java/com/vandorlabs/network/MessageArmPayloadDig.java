package com.vandorlabs.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageArmPayloadDig implements IMessage {
    private BlockPos root,cell;private boolean abort;
    public MessageArmPayloadDig(){}
    public MessageArmPayloadDig(BlockPos root,BlockPos cell,boolean abort){this.root=root;this.cell=cell;this.abort=abort;}
    @Override public void fromBytes(ByteBuf buffer){if(buffer.readableBytes()!=17)throw new IllegalArgumentException("Invalid arm mining packet length");root=BlockPos.fromLong(buffer.readLong());cell=BlockPos.fromLong(buffer.readLong());abort=buffer.readBoolean();}
    @Override public void toBytes(ByteBuf buffer){buffer.writeLong(root.toLong());buffer.writeLong(cell.toLong());buffer.writeBoolean(abort);}
    public static final class Handler implements IMessageHandler<MessageArmPayloadDig,IMessage> {
        @Override public IMessage onMessage(MessageArmPayloadDig message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{try{com.vandorlabs.gear.ArmPayloadHarvest.dig(player,message.root,message.cell,message.abort);}catch(IllegalArgumentException denied){player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(denied.getMessage()),true);}});return null;}
    }
}
