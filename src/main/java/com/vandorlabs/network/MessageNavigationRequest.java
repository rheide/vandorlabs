package com.vandorlabs.network;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;
/** Observation only. Viewport dimensions are derived by the server from the loaded assembly. */
public final class MessageNavigationRequest implements IMessage {
    private BlockPos pos;private int slot,serial,aspect,known;
    public MessageNavigationRequest(){}
    public MessageNavigationRequest(BlockPos pos,int slot,int serial,int aspect){this(pos,slot,serial,aspect,0);}
    public MessageNavigationRequest(BlockPos pos,int slot,int serial,int aspect,int known){this.pos=pos;this.slot=slot;this.serial=serial;this.aspect=aspect;this.known=known;}
    @Override public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeByte(slot);b.writeInt(serial);b.writeByte(aspect);b.writeInt(known);}
    @Override public void fromBytes(ByteBuf b){if(b.readableBytes()!=18)throw new IllegalArgumentException("Invalid navigation request");pos=BlockPos.fromLong(b.readLong());slot=b.readUnsignedByte();serial=b.readInt();aspect=b.readUnsignedByte();known=b.readInt();if(slot>1||aspect<8||aspect>64||known<0)throw new IllegalArgumentException("Invalid navigation surface");}
    public static final class Handler implements IMessageHandler<MessageNavigationRequest,IMessage>{public IMessage onMessage(MessageNavigationRequest m,MessageContext c){net.minecraft.entity.player.EntityPlayerMP p=c.getServerHandler().player;p.getServerWorld().addScheduledTask(()->com.vandorlabs.navigation.NavigationService.request(p,m.pos,m.slot,m.serial,m.aspect,m.known));return null;}}
}
