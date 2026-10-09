package com.vandorlabs.network;

import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.TileEntityShipSystem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Configure only the machine/group whose authorized dialog is still open. */
public final class MessageShipSystem implements IMessage {
    private BlockPos pos;
    private ChannelList channels;
    private int mode;
    public MessageShipSystem() {}
    public MessageShipSystem(BlockPos pos, ChannelList channels, int mode) { this.pos=pos; this.channels=channels; this.mode=mode; }
    public void toBytes(ByteBuf b) { b.writeLong(pos.toLong()); b.writeByte(mode); ChannelData.write(b, channels); }
    public void fromBytes(ByteBuf b) {
        if (b.readableBytes() < 10) return;
        pos=BlockPos.fromLong(b.readLong()); mode=b.readUnsignedByte(); channels=ChannelData.read(b,0);
    }
    public static final class Handler implements IMessageHandler<MessageShipSystem,IMessage> {
        public IMessage onMessage(MessageShipSystem message, MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if(message.pos==null || message.channels==null || message.mode<0 || message.mode>2
                        || !ConfigurationAccess.canConfigure(player) || !player.world.isBlockLoaded(message.pos)
                        || !(player.openContainer instanceof ContainerRedstoneChannel)) return;
                TileEntityShipSystem tile=TileEntityShipSystem.at(player.world,message.pos);
                ContainerRedstoneChannel container=(ContainerRedstoneChannel)player.openContainer;
                if(tile==null || container.member!=tile || !container.canInteractWith(player)) return;
                tile.configure(message.channels,message.mode);
            });
            return null;
        }
    }
}
