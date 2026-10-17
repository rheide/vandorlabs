package com.vandorlabs.network;

import com.vandorlabs.container.ContainerCockpit;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Only the currently open authenticated cockpit container accepts these edits. */
public final class MessageCockpit implements IMessage {
    private int window,operation;
    private NBTTagCompound values;
    public MessageCockpit(){}
    public MessageCockpit(int window,int operation,NBTTagCompound values){this.window=window;this.operation=operation;this.values=values;}
    public void fromBytes(ByteBuf b){window=b.readInt();operation=b.readUnsignedByte();values=ByteBufUtils.readTag(b);}
    public void toBytes(ByteBuf b){b.writeInt(window);b.writeByte(operation);ByteBufUtils.writeTag(b,values);}
    public static final class Handler implements IMessageHandler<MessageCockpit,IMessage>{
        public IMessage onMessage(MessageCockpit msg,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{
            if(msg.values==null || player.openContainer.windowId!=msg.window || !(player.openContainer instanceof ContainerCockpit))return;
            try{((ContainerCockpit)player.openContainer).update(player,msg.operation,msg.values);}catch(IllegalArgumentException denied){com.vandorlabs.vehicle.VehicleNetwork.error(player,denied.getMessage());}
        });return null;}
    }
}
