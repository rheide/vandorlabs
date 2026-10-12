package com.vandorlabs.network;
import com.vandorlabs.container.ContainerLargeCanopy;
import com.vandorlabs.canopy.LargeCanopyOwnership;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
public final class MessageLargeCanopy implements IMessage {
    private BlockPos pos;private int depth,scale,shade,tint;private boolean valid;
    public MessageLargeCanopy(){}
    public MessageLargeCanopy(BlockPos pos,int depth,int scale,int shade,int tint){this.pos=pos;this.depth=depth;this.scale=scale;this.shade=shade;this.tint=tint;valid=true;}
    public void toBytes(ByteBuf buffer){buffer.writeLong(pos.toLong());buffer.writeByte(depth);buffer.writeByte(scale);buffer.writeByte(shade);buffer.writeByte(tint);}
    public void fromBytes(ByteBuf buffer){try{pos=BlockPos.fromLong(buffer.readLong());depth=buffer.readUnsignedByte();scale=buffer.readUnsignedByte();shade=buffer.readUnsignedByte();tint=buffer.readUnsignedByte();valid=com.vandorlabs.canopy.LargeCanopySettings.valid(depth,scale,shade,tint);}catch(Exception failure){valid=false;}}
    public static final class Handler implements IMessageHandler<MessageLargeCanopy,IMessage>{public IMessage onMessage(MessageLargeCanopy message,MessageContext context){EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{if(!message.valid || !(player.openContainer instanceof ContainerLargeCanopy) || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player))return;ContainerLargeCanopy container=(ContainerLargeCanopy)player.openContainer;if(!container.canInteractWith(player) || !container.tile.getPos().equals(message.pos))return;if(!LargeCanopyOwnership.apply(container.tile,player,message.depth,message.scale,message.shade,message.tint))player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Canopy settings were not applied: the new footprint is obstructed or protected."),false);});return null;}}
}
