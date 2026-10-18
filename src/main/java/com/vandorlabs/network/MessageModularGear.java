package com.vandorlabs.network;

import com.vandorlabs.container.ContainerModularGear;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageModularGear implements IMessage {
    private int window;private NBTTagCompound values;
    public MessageModularGear(){}
    public MessageModularGear(int window,NBTTagCompound values){this.window=window;this.values=values;}
    @Override public void fromBytes(ByteBuf b){window=b.readInt();values=ByteBufUtils.readTag(b);}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);ByteBufUtils.writeTag(b,values);}
    public static final class Handler implements IMessageHandler<MessageModularGear,IMessage>{public IMessage onMessage(MessageModularGear message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{if(message.values==null||player.openContainer.windowId!=message.window||!(player.openContainer instanceof ContainerModularGear))return;try{((ContainerModularGear)player.openContainer).update(player,message.values);}catch(IllegalArgumentException denied){player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(denied.getMessage()),true);}});return null;}}
}
