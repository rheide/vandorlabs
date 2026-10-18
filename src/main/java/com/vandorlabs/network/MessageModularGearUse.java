package com.vandorlabs.network;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;
public final class MessageModularGearUse implements IMessage {
    private BlockPos source;
    public MessageModularGearUse(){}
    public MessageModularGearUse(BlockPos source){this.source=source;}
    public void fromBytes(ByteBuf buffer){source=BlockPos.fromLong(buffer.readLong());}
    public void toBytes(ByteBuf buffer){buffer.writeLong(source.toLong());}
    public static final class Handler implements IMessageHandler<MessageModularGearUse,IMessage>{public IMessage onMessage(MessageModularGearUse message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{try{com.vandorlabs.gear.GearInteractions.use(player,message.source);}catch(IllegalArgumentException denied){player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(denied.getMessage()),true);}});return null;}}
}
