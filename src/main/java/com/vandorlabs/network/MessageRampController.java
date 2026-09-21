package com.vandorlabs.network;

import com.vandorlabs.container.ContainerRampController;
import com.vandorlabs.tiles.TileEntityRampController;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageRampController implements IMessage {
    private BlockPos pos;
    private int drop,segments,direction,channel;
    private boolean top,powerOn,slow,elevator;
    public MessageRampController() { }
    public MessageRampController(BlockPos pos,int drop,int segments,boolean top,boolean powerOn,boolean slow,boolean elevator,
            net.minecraft.util.EnumFacing direction) {
        this(pos,drop,segments,top,powerOn,slow,elevator,direction,0);
    }
    public MessageRampController(BlockPos pos,int drop,int segments,boolean top,boolean powerOn,boolean slow,boolean elevator,
            net.minecraft.util.EnumFacing direction,int channel) {
        this.pos=pos; this.drop=drop; this.segments=segments;
        this.top=top; this.powerOn=powerOn; this.slow=slow; this.elevator=elevator;
        this.direction=direction.getHorizontalIndex();
        this.channel=channel;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos=BlockPos.fromLong(buf.readLong()); drop=buf.readInt(); segments=buf.readInt();
        top=buf.readBoolean(); powerOn=buf.readBoolean(); slow=buf.readBoolean(); elevator=buf.readBoolean();
        direction=buf.readInt();
        channel=buf.readableBytes()>=4?buf.readInt():0;
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong()); buf.writeInt(drop); buf.writeInt(segments);
        buf.writeBoolean(top); buf.writeBoolean(powerOn); buf.writeBoolean(slow); buf.writeBoolean(elevator);
        buf.writeInt(direction);
        buf.writeInt(channel);
    }
    public static class Handler implements IMessageHandler<MessageRampController,IMessage> {
        @Override public IMessage onMessage(MessageRampController message,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if (message.pos==null || !player.world.isBlockLoaded(message.pos)) return;
                TileEntity raw=player.world.getTileEntity(message.pos);
                if (!(raw instanceof TileEntityRampController) || !(player.openContainer instanceof ContainerRampController)) return;
                TileEntityRampController te=(TileEntityRampController)raw;
                if (((ContainerRampController)player.openContainer).controller!=te || !te.usable(player)) return;
                if (message.direction<0 || message.direction>3 || message.channel<0) return;
                te.configure(player,message.drop,message.segments,message.top,message.powerOn,message.slow,message.elevator,
                        net.minecraft.util.EnumFacing.getHorizontal(message.direction));
                te.setRedstoneChannel(message.channel);
                player.connection.sendPacket(te.getUpdatePacket());
            });
            return null;
        }
    }
}
