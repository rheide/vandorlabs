package com.vandorlabs.network;

import com.vandorlabs.container.ContainerTurntable;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageTurntableSettings implements IMessage {
    private int window,operation;private NBTTagCompound settings;
    public MessageTurntableSettings(){}
    public MessageTurntableSettings(int window,int operation,NBTTagCompound settings){this.window=window;this.operation=operation;this.settings=settings;}
    @Override public void fromBytes(ByteBuf b){window=b.readInt();operation=b.readUnsignedByte();settings=ByteBufUtils.readTag(b);}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeByte(operation);ByteBufUtils.writeTag(b,settings);}
    public static final class Handler implements IMessageHandler<MessageTurntableSettings,IMessage>{
        public IMessage onMessage(MessageTurntableSettings message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{
            if(message.settings==null||player.openContainer.windowId!=message.window||!(player.openContainer instanceof ContainerTurntable))return;
            ContainerTurntable container=(ContainerTurntable)player.openContainer;
            try{container.update(player,message.operation,message.settings);}catch(Exception failure){container.tile.failure(failure.getMessage());}
        });return null;}
    }
}
